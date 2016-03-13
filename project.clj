(defproject clubby "0.1.0-SNAPSHOT"
  :description "Clubhouse Reporting"
  :url "https://github.com/mikebroberts/clubby"
  :license {:name "Apache License"
            :url "https://github.com/mikebroberts/clubby/blob/master/LICENSE"}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [environ "1.0.1"]
                 [clj-http "2.0.0"]
                 [cheshire "5.5.0"]
                 [camel-snake-kebab "0.3.2"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [clj-time "0.11.0"]
                 [net.mikera/core.matrix "0.45.0"]
                 [org.clojure/core.memoize "0.5.8"]
                 ; For Web View
                 [compojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [hiccup "1.0.5"]
                 ]
  :plugins [
            [lein-environ "1.0.1"]
            ; For Web View
            [lein-ring "0.9.6"]]

  :main clubby.web
  :ring {:handler clubby.web/app}
  :uberjar-name "clubby-standalone.jar"
  )
