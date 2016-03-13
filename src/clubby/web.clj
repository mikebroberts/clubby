(ns clubby.web
  (:require [clojure.core.memoize :refer [ttl]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :as response]
            [environ.core :as env]
            [hiccup.core :refer :all]
            [ring.adapter.jetty :refer :all]
            [clubby.core :refer :all]
            [clubby.confluence :as confluence]
            [clojure-csv.core :as csv]
            ))

(defn generate-page-for-content [content]
  (str "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
       (html
         [:html {:lang "en"}
          [:head
           [:meta {:charset "utf-8"}]
           [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
           [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css" :integrity "sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7" :crossorigin "anonymous"}]
           ; Optional theme
           [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css" :integrity "sha384-fLW2N01lMqjakBkx3l/M9EahuwpSfeNvV63J5ezn3uZzapT0u7EYsXMjQV+0En5r" :crossorigin "anonymous"}]
           [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js" :integrity "sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS" :crossorigin "anonymous"}]
           ]
          [:body {:role "document" :style "padding-top: 70px;"} ; Padding required because of fixed-top nav bar
           (apply list content)
           ]
          ])))

(defn add-nav [& content]
  [[:nav.navbar.navbar-inverse.navbar-fixed-top
    [:div.container
     [:div.navbar-header
      [:a.navbar-brand {:href "/"} "Clubby"]
      ]]]
   [:div.container
    content
    ]])

(defn index []
  (generate-page-for-content (add-nav
                               [:h2 "Clubby"]
                               [:ul
                                (for [{:keys [name id]} (get-projects)]
                                  [:li [:a {:href (str "/projects/" id)} name]]
                                  )])))

(defn to-board-row [cells]
  [:tr
   (for [{:keys [name project_id id] :as cell} cells]
        [:td (when cell [:a {:href (web-url-for-story project_id id)} name])])
   ])

(defn html-kanban-board [xss]
  [:table.table.table-bordered
   [:tr (for [state (map :workflow_state (first xss))]
          [:th state]
          )]
   (map to-board-row xss)
   ])

(defn completed-stories-table [recently-completed-stories]
  [:table.table.table-striped
   [:tr [:th "Name"] [:th "State"] [:th "Time to Complete (hours"] [:th "Labels"]]
   (for [{:keys [project_id name id workflow_state cycle-time-hours labels]} recently-completed-stories]
     [:tr
      [:td [:a {:href (web-url-for-story project_id id)} name]]
      [:td workflow_state]
      [:td cycle-time-hours]
      [:td (clojure.string/join " " (map :name labels))]
      ])])

(defn ipm-page-layout [project-id {name :name} kanban-stories recently-completed-stories {:keys [iteration-length]}]
  (generate-page-for-content (add-nav
                               [:h3 "Project History"]
                               [:div "Click " [:a {:href (str "/project-history/" project-id)} "here"] " for history by week"]
                               [:div "Click " [:a {:href (str "/stories-history/" project-id)} "here"] " for history by story"]
                               [:h2 "Current Status for " name]
                               (html-kanban-board kanban-stories)
                               [:h2 (str "Stories completed in last " iteration-length " days")]
                               (completed-stories-table recently-completed-stories)
                               [:h2 (str "IPM Report")]
                               [:h3 "Confluence Format:"]
                               [:pre (confluence/ipm-report kanban-stories recently-completed-stories)]
                               )))

(defn ipm-page [project-id lookback-days]
  (let [{:keys [project-id] :as config} (config-for project-id lookback-days)]
    (ipm-page-layout
      project-id
      (get-project project-id)
      (kanban-story-summaries config)
      (recently-completed-stories config)
      config
      )))

(defn stories-by-week->csv [stories-by-week]
  (csv/write-csv (concat [["Week" "Mean completion Time" "Stories completed"]]
                         (map (fn [[weekyear mean-completion-time stories]]
                                [weekyear (str (int mean-completion-time)) (clojure.string/join " " (map :id stories))]
                                )
                              stories-by-week))
                 :force-quote true)
  )

(defn stories-by-week->json [stories-by-week]
  {:weeks
   (map (fn [[weekyear mean-completion-time stories]]
          {:week                       weekyear
           :mean-completion-time-hours (int mean-completion-time)
           :stories                    (map #(assoc % :story-url (web-url-for-story (:project_id %) (:id %))) stories)
           })
        stories-by-week)
   })

(defn history-page-layout [project-id {name :name} stories-by-week]
  (generate-page-for-content (add-nav
                               [:h2 "History for " name]
                               [:table.table.table-striped
                                [:tr [:th "Week"] [:th "Mean completion time"] [:th "Stories completed"] [:th "Labels"] [:th "Epics"]]
                                (for [[weekyear mean-completion-time stories labels epics] stories-by-week]
                                  [:tr
                                   [:td weekyear]
                                   [:td (int mean-completion-time)]
                                   [:td (for [{story-id :id} stories] [:a {:href (web-url-for-story project-id story-id)} (str story-id " ")])]
                                   [:td (for [{name :name} labels] (str name " "))]
                                   [:td (for [{:keys [name id]} epics] [:a {:href (web-url-for-epic project-id id)} (str name " ")])]
                                   ])
                                ]
                               [:div.alert.alert-info "Results are up to 5 minutes old if others have viewed recently"]
                               [:h3 "Alternate formats"]
                               [:div [:a {:href (str "/project-history/" project-id "/json")} "JSON"]]
                               [:div "CSV:"]
                               [:pre (stories-by-week->csv stories-by-week)]
                               )))

; Cache all completed stories by week for 60 seconds
(def memoized-all-completed-stories-by-week
  (ttl all-completed-stories-by-week {} :ttl/threshold 300000))

(defn history-page [project-id]
  (let [config (config-for project-id nil)]
    (history-page-layout
      project-id
      (get-project (:project-id config))
      (memoized-all-completed-stories-by-week config)
      )))

(defn history-json [project-id]
  (let [config (config-for project-id nil)]
    (response/response (stories-by-week->json (memoized-all-completed-stories-by-week config)))))

; Cache all completed stories for 60 seconds
(def memoized-all-completed-stories
  (ttl all-completed-stories {} :ttl/threshold 300000))

(defn all-stories->csv [all-stories]
  (csv/write-csv (concat [["ID" "Completed" "Cycle time (hours)" "Name"]]
                         (map (fn [{story-id :id completed-at :completed_at cycle-time :cycle-time-hours story-name :name}]
                                [(str story-id) completed-at (str cycle-time) story-name]
                                )
                              all-stories
                              ))
                 :force-quote true)
  )

(defn stories-page-layout [project-id {name :name} stories-by-week]
  (generate-page-for-content (add-nav
                               [:h2 "Stories for " name]
                               [:table.table.table-striped
                                [:tr [:th "ID"] [:th "Completed date"] [:th "Cycle time (hours)"] [:th "Name"]]
                                (for [{story-id :id completed-at :completed_at cycle-time :cycle-time-hours story-name :name} stories-by-week]
                                  [:tr
                                   [:td [:a {:href (web-url-for-story project-id story-id)} story-id]]
                                   [:td (subs completed-at 4 10)]
                                   [:td (int cycle-time)]
                                   [:td story-name]
                                   ])
                                ]
                               [:div.alert.alert-info "Results are up to 5 minutes old if others have viewed recently"]
                               [:h3 "Alternate formats"]
                               [:div [:a {:href (str "/stories-history/" project-id "/json")} "JSON"]]
                               [:div "CSV:"]
                               [:pre (all-stories->csv stories-by-week)]
                               ))
  )

(defn stories-page [project-id]
  (let [config (config-for project-id nil)]
    (stories-page-layout
      project-id
      (get-project (:project-id config))
      (memoized-all-completed-stories config)
      )))

(defn stories-json [project-id]
  (->> (config-for project-id nil)
       memoized-all-completed-stories
       (map #(select-keys % [:id :completed_at :cycle-time-hours :name]))
       response/response))

(defn app-routes []
  (apply routes [
                 (GET "/" [] (index))
                 (GET "/projects/:id" [id lookback-days] (ipm-page id (when lookback-days (Integer. lookback-days))))
                 (GET "/stories-history/:id" [id] (stories-page id))
                 (GET "/stories-history/:id/json" [id] (stories-json id))
                 (GET "/project-history/:id" [id] (history-page id))
                 (GET "/project-history/:id/json" [id] (history-json id))
                 (route/not-found "Not Found")
                 ]))

(defn create-app []
  (wrap-defaults (wrap-json-response (app-routes)) site-defaults))

(defn -main []
  (let [port (if-let [port (env/env :port)] port 3001)]
    (println "Starting web server on port" port)
    (run-jetty (create-app) {:port (Integer. port)})))

; This is provided for the lein ring plugin
(def app
  (create-app))
