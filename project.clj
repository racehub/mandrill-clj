(defproject racehub/mandrill-clj "0.1.0"
  :description "Schemafied Mandrill bindings for Clojure."
  :url "https://github.com/racehub/mandrill-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.3.1"]
                 [environ "0.5.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [prismatic/schema "0.3.0"]]
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :profiles {:1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha2"]]}
             :dev {:injections [(require 'schema.core)
                                (schema.core/set-fn-validation! true)]
                   :plugins [[paddleguru/lein-gitflow "0.1.2"]]}})
