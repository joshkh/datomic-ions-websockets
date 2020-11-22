(ns wsdemo.handlers
  "Lambda / Ion functions to control websocket access to an AWS API Gateway, and store connection states in Datomic"
  (:require [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [datomic.ion.cast :as cast]
            [wsdemo.client :as client]
            [datomic.ion.lambda.api-gateway :refer [ionize]])
  (:import java.util.UUID))

; In the real world, the secret value used to sign and verify out JWTs
; would be stored outside of version control -- perhaps in AWS Parameter Store
(def secret (:secret (read-string (slurp (io/resource "project-config.edn")))))


(defn gateway-authorizer
  "A lambda fn to verify the JWT when a client attempts to connect to the API Gateway"
  [{:keys [input] :as req}]
  (try
    (let [{:keys [methodArn headers]} (json/read-str input :key-fn keyword)]
     ; set the policy's principal ID to the subject in the JWT
     (let [sub (-> headers :Authorization (jwt/unsign secret) :sub)]
       (json/write-str {:principalId    sub
                        :policyDocument {:Version   "2012-10-17",
                                         :Statement [{:Action   "execute-api:Invoke",
                                                      :Effect   "Allow",
                                                      :Resource methodArn}]}})))
    (catch Exception _
      (json/write-str {
                       :policyDocument {:Version   "2012-10-17",
                                        :Statement [{:Action   "execute-api:Invoke",
                                                     :Effect   "Deny",
                                                     :Resource "*"}]}}))))

(defn handle-connection
  "Web handler that returns info about items matching type."
  [{{{:keys [authorizer eventType connectionId]} :requestContext} :datomic.ion.edn.api-gateway/data}]
  (cast/event {:msg "WebSocketConnection"})
  (let [

        ; our Authorizer function returns a policy with the user's UUID string value as the Principal ID
        ; convert it to a java.util.UUID to be used with Datomic
        user-uuid (some-> authorizer :principalId UUID/fromString)

        ; determine which action to take in Datomic. CONNECT adds a Connection ID, DISCONNECT retracts a Connection ID
        action    (case eventType "CONNECT" :db/add "DISCONNECT" :db/retract)]

    (try
      ; add or remove a websocket Connection ID to a user entity based on the event type
      (when (:db-after (d/transact (client/get-conn)
                         {:tx-data [(case action
                                      :db/add {:user/id           user-uuid
                                               :ws/connection-ids [connectionId]}
                                      :db/retract [:db/retract [:user/id user-uuid] :ws/connection-ids connectionId])]}))
        ; and return a 200 status code on success
        {:statusCode 200})
      (catch Exception _
        ; or return a bad request if the transaction failed. the user's UUID probably doesn't exist.
        {:statusCode 400}))))

; Ionize the handle-connection function which converts input JSON to EDN, and output EDN back to JSON
(def handle-connection-proxy (ionize handle-connection))

