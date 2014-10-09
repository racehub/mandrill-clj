(ns mandrill.core
  "Methods for interacting with the Mandrill API."
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [org.httpkit.client :as http]
            [mandrill.schema :as ms]
            [schema.core :as s])
  (:import [clojure.lang ISeq]))

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

(s/defschema ApiToken
  (s/named s/Str "API key for Mandrill."))

(s/defschema HttpKitOptions
  "Options for Http-Kit's client."
  {s/Any s/Any})

(s/defschema MandrillParams
  "Post data or Get req data for Mandrill."
  {s/Any s/Any})

(s/defschema ApiCall
  {(s/optional-key :out-ch) (s/either (s/eq :ignore) (ms/Channel))
   (s/optional-key :mandrill-params) MandrillParams
   (s/optional-key :client-options) HttpKitOptions
   (s/optional-key :token) ApiToken})

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
   :from {:email Address
          :name s/Str}
   :subject s/Str
   :body (s/either s/Str [s/Str])
   (s/optional-key :attachments) [Attachment]})

;; ## Core Config

(def ^:dynamic url "https://mandrillapp.com/api/1.0/")

(def ^:dynamic *token* nil)

(s/defn api-token :- (s/maybe ApiToken)
  []
  *token*)

(defmacro with-token [k & forms]
  `(binding [*token* ~k]
     ~@forms))

(defn use-token!
  "Permanently sets a base token. The token can still be overridden on
  a per-thread basis using with-token."
  [s]
  (alter-var-root #'*token* (constantly s)))

;; ## Private

(s/defn method-url :- s/Str
  "URL for calling a method."
  [method :- s/Str]
  (str url method ".json"))

(s/defn prepare-params :- {s/Any s/Any}
  [key :- ApiToken
   params :- MandrillParams
   opts :- HttpKitOptions]
  (let [base-params  {:headers {"Content-Type" "application/json; charset=utf-8"}
                      :body (json/generate-string (assoc params :key key))}]
    (merge base-params opts)))

;; ## Public Methods

(s/defn api-call :- (ms/Async)
  "Call an API method on Mandrill. If :out-ch is supplied, places the
  result into that channel. Otherwise, returns the value
  directly. If :out-ch is `:ignore`, just sends the email and returns
  a future.

  The available options are described in the ApiCall schema.

  Example calls:

  (api-call \"users/ping\" {:mandrill-params {:id \"a1a1a1a1a1\"}})

  (api-call \"users/ping\" {:mandrill-params {:id \"a1a1a1a1a1\"}
                            :client-options {:socket-timeout 1000}))"
  [endpoint {:keys [mandrill-params client-options token out-ch]
             :or {mandrill-params {}
                  client-options {}
                  token (api-token)}} :- ApiCall]
  (assert token "API Token must not be nil.")
  (let [url (method-url endpoint)
        params (prepare-params token mandrill-params client-options)
        process (fn [ret]
                  (json/parse-string (:body ret) keyword))]
    (cond
     (not out-ch) (process @(http/post url params))
     (= out-ch :ignore) (http/post url params)
     :else (do (http/post url params #(a/put! out-ch (process %)))
               out-ch))))

(s/defn send-message :- (ms/Async)
  "Send a message. Pass in a message map.

  See:

  https://mandrillapp.com/api/docs/messages.html#method=send"
  ([email :- Email]
     (send-message email {}))
  ([{:keys [to-email subject body from] :as email} :- Email
     opts :- ApiCall]
     (let [{from-email :email from-name :name} from]
       (when-let [to-email (not-empty (ms/collectify to-email))]
         (let [message {:html (mail body)
                        :from_email from-email
                        :from_name from-name
                        :to (map (fn [e] {:email e}) to-email)
                        :track_opens true
                        :track_clicks true
                        :auto_text true
                        :inline_css true
                        :attachments (:attachments email [])
                        :subject subject}]
           (api-call "messages/send"
                     (assoc opts
                       :mandrill-params {:message message})))))))

(def Template
  {:template s/Str
   :message s/Str
   (s/optional-key :content) [s/Str]})

(s/defn send-template :- (ms/Async)
  "Send a message based on a template. Pass in a template name,
   message map and an optional template-content array.

  See:

  https://mandrillapp.com/api/docs/messages.html#method=send-template"
  ([template :- Template]
     (send-template template {}))
  ([{:keys [template message content]} :- Template
    opts :- ApiCall]
     (api-call "messages/send-template"
               {assoc opts
                :mandrill-params {:template_name template
                                  :message message
                                  :template_content (or content [])}})))

(s/defn user-info :- (ms/Async)
  "Get information about your Mandrill account.

  See: https://mandrillapp.com/api/docs/users.html#method=info"
  ([] (user-info {}))
  ([opts :- ApiCall]
     (api-call "users/info" opts)))

(s/defn ping :- (ms/Async)
  "Validate an API key and respond to a ping.

  See:

  https://mandrillapp.com/api/docs/users.html#method=ping"
  ([] (ping {}))
  ([opts :- ApiCall]
     (api-call "users/ping" opts)))

(s/defn senders :- (ms/Async)
  "Return the senders that have tried to use this account, both
   verified and unverified.

  See:

  https://mandrillapp.com/api/docs/users.html#method=senders"
  ([] (senders {}))
  ([opts :- ApiCall]
     (api-call "users/senders" opts)))
