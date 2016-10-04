(defproject ring-spec "0.1.0-SNAPSHOT"
  :description "Clojure specs for Ring"
  :url "https://github.com/ring-clojure/ring-spec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [ring/ring-core "1.6.0-beta6"]]
  :profiles
  {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
