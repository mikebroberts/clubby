(ns clubby.core
  (:require
    [clojure.core.memoize :refer [ttl]]
    [environ.core :as env]
    [cheshire.core :as json]
    [clj-http.client :as client]
    [camel-snake-kebab.core :refer [->snake_case]]
    [clojure-csv.core :as csv]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.core.matrix :as matrix]
    )
  (:gen-class))

; ** Clubhouse interaction

(defn token []
  (env/env :clubhouse-key))

; Can we get this from a meta-data request?
(def api-base-url "https://api.clubhouse.io/api/v1/")

(defn getc-uncached [request]
  (->
    (client/get
      (str api-base-url request "?token=" (token)))
    :body
    (json/parse-string true)))

; Cache all calls by default for 10 seconds
(def getc
  (ttl getc-uncached {} :ttl/threshold 10000))

(defn get-stories-for-project [project-id]
  (getc (str "projects/" project-id "/stories")))

(defn get-story-state [story-id]
  (getc (str "stories/" story-id "/history/story-states")))

(defn get-projects []
  (getc "projects"))

(defn get-project [project-id]
  (getc (str "projects/" project-id)))

(defn get-workflows []
  (getc "workflows"))

(defn get-labels []
  (getc "labels"))

(defn get-epics []
  (getc "epics"))

(defn url-slug-uncached [project-id]
  (when (nil? project-id)
    (throw (Exception. "Nil project id - can't discern project url slug")))
  (-> (get-project project-id) :organization :url_slug))

(def url-slug
  (ttl url-slug-uncached {} :ttl/threshold 60000))

(defn web-url-for-entity [project-id entity-type id]
  (str "https://" (url-slug project-id) ".clubhouse.io/" entity-type "/" id))

(defn web-url-for-story [project-id id]
  (web-url-for-entity project-id "story" id))

(defn web-url-for-epic [project-id id]
  (web-url-for-entity project-id "epic" id))

; ** Projects

(defn project-summary []
  (->>
    (get-projects)
    (map #(select-keys % [:name :id]))))

; ** Selectors

(defn label-id-by-label-name [label-name]
  (:id (first (filter #(= label-name (:name %)) (get-labels)))))

(defn filter-stories-by-label [label-name stories]
  (let [label-id (label-id-by-label-name label-name)]
    (filter (fn [{labels :labels}] (some #(= label-id (:id %)) labels)) stories)))

(defn get-stories-for-project-by-label [project-id label-name]
  (filter-stories-by-label label-name (get-stories-for-project project-id)))

; ** Story summaries

(defn get-workflow-states []
  (->> (get-workflows)
       (first)
       (:states)
       (map (fn [{:keys [name id]}] {name id}))
       ))

(defn convert-states-to-state-ids [all-states states]
  (map (partial get all-states) states))

(defn in-workflow-state? [candidate-state-ids {workflow_state_id :workflow_state_id}]
  (some #{workflow_state_id} candidate-state-ids))

(defn get-stories-for-project-in-states [project-id state-ids]
  (filter (partial in-workflow-state? state-ids) (get-stories-for-project project-id)))

; Additional fields are those more than name, ID and workflow_state
(defn story-summaries [project-id states & additional-fields]
  (let [desired-fields (concat [:name :id :workflow_state :project_id] additional-fields)
        workflow-state-list (get-workflow-states)
        workflow-states (apply merge workflow-state-list)
        reverse-workflow-states (clojure.set/map-invert workflow-states)
        workflow-state-name-list (map (comp first first) workflow-state-list)
        ]
    (->>
      (convert-states-to-state-ids workflow-states states)
      (get-stories-for-project-in-states project-id)
      (map (fn [story] (assoc story :workflow_state (get reverse-workflow-states (:workflow_state_id story)))))
      (map (fn [story] (select-keys story desired-fields)))
      (sort-by (fn [story] (.indexOf workflow-state-name-list (:workflow_state story))))
      )))

; Creates a rectangular array, padding with nil to fill in jaggedness
; This is required to enable transposition without losing data
(defn pad-shorter-seqs-with-nil [xss]
  (let [max-seq-length (apply max (map count xss))]
    (map (fn [xs] (concat xs (take (- max-seq-length (count xs)) (repeat nil)))) xss)))

(defn kanban-story-summaries [{:keys [project-id unstarted-ready-states started-states kanban-completed-states]}]
  (->> (story-summaries project-id (concat unstarted-ready-states started-states kanban-completed-states))
       (group-by :workflow_state)
       (map second)
       (pad-shorter-seqs-with-nil)
       (matrix/transpose)))

(defn occurred-within-last-n-days [event-key formatter days story]
  (let [event-time (time-format/parse formatter (get story event-key))]
    (time-core/within? (time-core/interval event-time (time-core/plus event-time (time-core/days days)))
                       (time-core/now))))

(def state-time-formatter (time-format/formatter "EEE MMM dd HH:mm:ss zzz YYYY"))

(defn updated-in-last-n-days [days story]
  (occurred-within-last-n-days :updated_at (time-format/formatters :date-time-no-ms) days story))

(defn completed-in-last-n-days [days story]
  (occurred-within-last-n-days :completed_at state-time-formatter days story))

(defn story-state-history [id]
  (filter #(= "workflow_state_added" (:event_type %)) (get-story-state id)))

(defn story-state-is-one-of-states [story-states story-state]
  (some #{(:value story-state)} story-states))

; Returns the earliest time a story was in any of the started or completed states,
; or nil if it never has been
(defn story-started-at [started-states complete-states history]
  (->> history
       (filter (partial story-state-is-one-of-states (concat complete-states started-states)))
       last
       :changed_at
       ))

; Assumes history in reverse chronological order
; Complete-states must included *all* candidate complete states (e.g. :finished, :delivered and :accepted)
; Returned time is earliest time story was in one of these states, but not earlier than any time
; a story was in any other state. Corollary - returns nil if story not currently in a complete state
(defn story-complete-at [complete-states history]
  (->> history
       (take-while (partial story-state-is-one-of-states complete-states))
       last
       :changed_at
       ))

(defn time-difference [t2 t1]
  (when (and t2 t1)
    (time-core/in-hours (time-core/interval
                          (time-format/parse state-time-formatter t1)
                          (time-format/parse state-time-formatter t2)))))

(defn add-story-started-and-complete [started-states done-states s]
  (let [history (story-state-history (:id s))
        started (story-started-at started-states done-states history)
        complete (story-complete-at done-states history)]
    (assoc s :started_at started :completed_at complete :cycle-time-hours (time-difference complete started) :state-history history)
    ))

(defn recently-completed-stories [{:keys [project-id iteration-length started-states completed-states]}]
  (->>
    ; Get all complete stories
    (story-summaries project-id completed-states :updated_at :labels :project_id)
    ; First coarse grained filter, just to avoid looking up history for stories we don't care about
    (filter (partial updated-in-last-n-days iteration-length))
    ; Calculate completed time (in parallel since we're making a call for each to clubhouse)
    (pmap (partial add-story-started-and-complete started-states completed-states))
    ; Remove all stories not completed in last iteration
    (filter (partial completed-in-last-n-days iteration-length))
    ; Sort by most recently completed first
    (sort-by (comp (partial time-format/parse state-time-formatter) :completed_at))
    (reverse)))

(defn calc-mean-completion-time [finished-stories]
  (let [num-finished-stories (count finished-stories)]
    (if (> num-finished-stories 0)
      (/ (reduce + (map :cycle-time-hours finished-stories)) num-finished-stories)
      0)))

(defn add-weekyear-week [story]
  (->> story
       :completed_at
       (time-format/parse state-time-formatter)
       (time-format/unparse (time-format/formatters :weekyear-week))
       (assoc story :weekyear-week)
       ))

(defn resolve-epic [epics story]
  (update-in story [:epic] (partial map (fn [epic]
                                        (->> epics
                                             (filter #(= (:id epic) (:id %)))
                                             first
                                             :name
                                             (assoc epic :name)
                                             )))))

(defn items-from [item-key stories]
  (apply clojure.set/union (map set (map (partial map #(select-keys % [:id :name]))(map item-key stories)))))

(defn all-completed-stories [{:keys [project-id started-states completed-states]}]
  (let [epics (get-epics)]
    (->>
      ; Get all complete stories
      (story-summaries project-id completed-states :updated_at :labels :epic)
      ; Calculate completed time, add week-year for each story when it was completed
      ; In parallel since we're calling Clubhouse for each story
      (pmap (comp (partial resolve-epic epics) add-weekyear-week (partial add-story-started-and-complete started-states completed-states)))
      ; Sort by most recently completed first
      (sort-by (comp (partial time-format/parse state-time-formatter) :completed_at))
      reverse
  )))

(defn all-completed-stories-by-week [config]
  (let [stories (all-completed-stories config)]
    (->> stories
      ; Group them into weeks
      (group-by :weekyear-week)
      ; And for each week we have a reformatted week descriptor, the mean completion time,
      ; a list of the story IDs completed in that week, and the labels and epics marked in those stories
      (map (fn [[weekyear-week stories]] [(->> weekyear-week
                                               (time-format/parse (time-format/formatters :weekyear-week))
                                               (time-format/unparse (time-format/formatters :year-month-day)))
                                          (calc-mean-completion-time stories)
                                          (map #(select-keys % [:id :labels :epic :project_id]) stories)
                                          (items-from :labels stories)
                                          (items-from :epic stories)
                                          ]))
      ; Then sort, most recent week first
      (sort-by first)
      reverse
      )))

(defn config-for [project-id lookback-days]
  {
   :project-id              project-id
   :iteration-length        (or lookback-days 7)
   :unstarted-ready-states  (or (env/env :default-unstarted-ready-states) ["Unstarted"])
   :started-states          (or (env/env :default-started-states) ["Started"])
   :kanban-completed-states (or (env/env :default-kanban-completed-states) ["Finished"])
   :completed-states        (or (env/env :default-completed-states) ["Finished" "Delivered" "Accepted"])
   })

; -- CSV --

(defn story->pre-csv [{:keys [id workflow_state started_at completed_at cycle-time-hours] :as story}]
  [(:name story) (str id) workflow_state
   (time-format/unparse (time-format/formatters :date-hour-minute) (time-format/parse state-time-formatter started_at))
   (time-format/unparse (time-format/formatters :date-hour-minute) (time-format/parse state-time-formatter completed_at))
   (str cycle-time-hours)])

(defn stories->csv [stories]
  (->>
    (csv/write-csv (concat [["Name" "ID" "Workflow-State" "Started At" "Completed At" "Cycle Time Hours"]] (map story->pre-csv stories)) :force-quote true)
    (spit "stories.csv")))

