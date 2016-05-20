(defproject clubby-app "0.1.0-SNAPSHOT"
  :description "Clubhouse Reporting"
  :url "https://github.com/mikebroberts/clubby"
  :license {:name "Apache License"
            :url "https://github.com/mikebroberts/clubby/blob/master/LICENSE"}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [clubby "1.0.4"]
                 ]
  :plugins [
            [lein-environ "1.0.1"]
            [lein-ring "0.9.6"]
            ]
  :main clubby.web
  :aot [clubby.web]
  :ring {:handler clubby.web/app}
  :uberjar-name "clubby-standalone.jar"
  )
