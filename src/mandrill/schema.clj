(ns mandrill.schema
  "TODO: Move this into RaceHub's util library and share."
  (:require [schema.core :as s]
            [clojure.core.async])
  (:import [clojure.core.async.impl.protocols ReadPort]))

(defn Channel
  "Takes a schema and returns a schema for a channel. The inner
  schema is ignored, and just for documentation purposes."
  ([] (Channel s/Any))
  ([inner]
     (s/named ReadPort "core.async channel.")))

(defn Async
  "Takes a schema and returns an either schema for the passed-in inner
  schema OR a channel. If the Stripe method called is async, The inner
  schema is ignored, and just for documentation purposes. If not, the
  inner schema is used."
  ([] (Async s/Any))
  ([inner]
     (s/either inner (Channel inner))))

(defn collectify [obj]
  (cond (nil? obj) []
        (or (sequential? obj) (instance? java.util.List obj) (set? obj)) obj
        :else [obj]))
