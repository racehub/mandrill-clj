(ns mandrill.test
  (:require [environ.core :as e]
            [mandrill.core :as m]))

(defn env-token
  "Clojure.test fixture that sets the Mandrill API token for all tests
  using the environment variable MANDRILL_DEV_TOKEN.

  Use like: (clojure.test/use-fixtures :once t/env-token)"
  [test-fn]
  (m/with-token (:mandrill-dev-token e/env)
    (test-fn)))
