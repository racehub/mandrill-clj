(ns mandrill.core
  "Methods for interacting with the Mandrill API."
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [org.httpkit.client :as http]
            [paddleguru.config :as conf]
            [paddleguru.schema :refer [Channel]]
            [paddleguru.util :as u]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import (clojure.lang ISeq)))

;; ## Schemas

(defprotocol Mailable
  (mail [this]
    "Coerces the body into HTML suitable for sending via email."))

(extend-protocol Mailable
  nil
  (mail [_] nil)
  String
  (mail [s] s)
  ISeq
  (mail [coll] (apply str coll)))

(def Key (s/named s/Str "API key for Mandrill."))

(def Address
  "TODO: Have this actually use a schema that detects if we're dealing
   with an email address or not."
  (s/named s/Str "email address."))

(def Attachment
  {:type s/Str
   :name s/Str
   :content s/Str})

(def Email
  {:to-email (s/either Address [Address])
   (s/optional-key :from-email) Address
   (s/optional-key :attachments) [Attachment]
   :subject s/Str
   :body (s/either s/Str [s/Str])})

;; ## Private

(s/defn conf-key :- Key
  []
  (:key (conf/get-config :mandrill)))

(def mandrill-url
  "https://mandrillapp.com/api/1.0/")

(s/defn method-url
  "URL for calling a method"
  [method :- String]
  (str mandrill-url method ".json"))

(defn prepare-params
  [key params opts]
  (let [base-params  {:headers {"Content-Type" "application/json; charset=utf-8"}
                      :body (json/generate-string (assoc params :key key))}]
    (merge base-params opts)))

;; ## Public Methods

(s/defn api-call :- Channel
  "Call an API method on Mandrill. Returns a core.async channel

  The available options are:
  Example calls:

  (api-call \"users/ping\" {:id \"a1a1a1a1a1\"})

  (api-call \"users/ping\" {:id \"a1a1a1a1a1\"} {:socket-timeout 1000})

  (api-call \"users/ping\" {:id \"a1a1a1a1a1\"} {:socket-timeout 1000})"
  [key :- Key method :- s/Str & [params opts]]
  (let [c (a/chan)
        url (method-url method)
        params (prepare-params key params opts)]
    (http/post url params
               (fn [ret]
                 (a/put! c (json/parse-string (:body ret)))))
    c))

(s/defn send-message :- Channel
  "Send a message. Pass in a message map.

  See:

  https://mandrillapp.com/api/docs/messages.html#method=send"
  [key :- Key {:keys [to-email subject body from-email]
               :as email
               :or {from-email "support@paddleguru.com"}} :- Email]
  (when-let [to-email (not-empty (u/collectify to-email))]
    (let [message {:html (mail body)
                   :from_email from-email
                   :from_name "PaddleGuru"
                   :to (map (fn [e] {:email e}) to-email)
                   :track_opens true
                   :track_clicks true
                   :auto_text true
                   :inline_css true
                   :attachments (:attachments email [])
                   :subject subject}]
      (api-call key "messages/send" {:message message}))))

(s/defn send-template :- Channel
  "Send a message based on a template . Pass in a template name,
   message map and an optional template-content array.

  See:

  https://mandrillapp.com/api/docs/messages.html#method=send-template"
  ([key :- Key template message]
     (send-template template message []))
  ([key :- Key template message template-content]
     (api-call "messages/send-template" {:template_name template
                                         :message message
                                         :template_content template-content})))

(s/defn user-info :- Channel
  "Get information about your Mandrill account.

  See: https://mandrillapp.com/api/docs/users.html#method=info"
  [key :- Key]
  (api-call key "users/info"))

(s/defn ping :- Channel
  "Validate an API key and respond to a ping.

  See:

  https://mandrillapp.com/api/docs/users.html#method=ping"
  [key :- Key]
  (api-call key "users/ping"))

(s/defn senders :- Channel
  "Return the senders that have tried to use this account, both
   verified and unverified.

  See:

  https://mandrillapp.com/api/docs/users.html#method=senders"
  [key :- Key]
  (api-call key "users/senders"))

(s/defn send-email :- Channel
  "Accepts a recipient email (potentially many), a subject line and a
  body (in text form) and sends the message from
  support@paddleguru.com."
  [email :- Email]
  (let [key (:key (conf/get-config :mandrill))]
    (log/info "send-email" {:email (dissoc email :body :attachments)})
    (send-message key email)))
