(ns mandrill.test
  (:require [environ.core :as e]
            [mandrill.core :as m]))

(defn env-token
  "Clojure.test fixture that sets the Mandrill API token for all tests
  using the environment variable linked to the supplied keyword.

  Use like: (clojure.test/use-fixtures :once (t/env-token :mandrill-dev-token))"
  [k]
  (fn [test-fn]
    (m/with-token (k e/env)
      (test-fn))))
