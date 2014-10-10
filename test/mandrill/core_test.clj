(ns mandrill.core-test
  (:use clojure.test
        mandrill.core)
  (:require [clojure.core.async :as a]))

(deftest ping-test
  (is (= "PONG!" (ping)) "Ping returns a pong, synchronously.")
  (is (= "PONG!" (ping)
         (read-string (:body @(ping {:out-ch :ignore})))
         (a/<!! (ping {:out-ch (a/chan)})))
      "Ignoring the output channel returns a future of the entire
      response. Supplying the output channel brings in core.async."))
