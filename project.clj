(defproject carcassonne "1.0-SNAPSHOT"
  :description "Carcassonne game rules and scoring engine"
  :url "https://github.com/djui/carcassonne"
  :license {:name "MIT License"
            :url "https://github.com/djui/carcasssonne/blob/master/LICENSE"}
  :aot [carcassonne.core]
  :main ^:skip-aot carcassonne.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :dependencies [[aleph "0.3.2"]
                 [com.taoensso/timbre "3.1.6"]
                 ;;[egamble/let-else "1.0.6"]
                 [org.clojure/clojure "1.6.0"]
                 ;;[org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [org.clojure/data.generators "0.1.2"]
                 [org.clojure/data.json "0.2.4"]])
  
