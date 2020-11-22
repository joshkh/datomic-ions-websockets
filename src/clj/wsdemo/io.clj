(ns wsdemo.io
  (:require [clojure.java.io :as io]
            [cognitect.aws.client.api :as aws]
            [datomic.client.api :as d]
            [datomic.ion.lambda.api-gateway :refer [ionize]]
            [wsdemo.client :as client]))

(def gw (aws/client {:api               :apigatewaymanagementapi
                     :endpoint-override (:api-gw (read-string (slurp (io/resource "project-config.edn"))))}))

(defn all-connection-ids
  "Return a collection of all client connection IDs"
  [db]
  (map first (d/q '{:find  [?id]
                    :in    [$]
                    :where [[?p :ws/connection-ids ?id]]}
               db)))

(defn clear-all-connections-tx-data
  "Return a collection of all client connection IDs"
  [db]
  (map (fn [[entity connection-id]]
         [:db/retract entity :ws/connection-ids connection-id])
    (d/q '{:find  [?p ?id]
           :in    [$]
           :where [[?p :ws/connection-ids ?id]]}
      db)))

(defn user-connection-ids
  "Return a collection of a user's connection IDs"
  [db user-uuid]
  (map first (d/q '{:find  [?id]
                    :in    [$ ?user-uuid]
                    :where [[?p :user/id ?user-uuid]
                            [?p :ws/connection-ids ?id]]}
               db user-uuid)))

(defn post-to-connection
  "Post a message to a client connected to the API Gateway"
  [id message]
  (aws/invoke gw {:op      :PostToConnection
                  :request {:ConnectionId id
                            :Data         message}}))

(defn post-to-user
  "Post a message to all of a single user's client connection IDs"
  [db user-uuid message]
  (let [ids (user-connection-ids db user-uuid)]
    (for [id ids]
      (post-to-connection id message))))

(defn broadcast
  "Broadcast a message to all connected clients"
  [db message]
  (for [connection-id (all-connection-ids db)]
    (post-to-connection connection-id (prn-str message))))

(defmulti dispatch-message :action)

(defmethod dispatch-message :broadcast
  [{:keys [uid message]}]
  (broadcast (client/db) {:message message
                          :sender  uid})
  {:statusCode 200})

(defmethod dispatch-message :default
  []
  {:statusCode 200})

(defn handle-message
  "Send a message to all users"
  [{{body                                   :body
     {connection-id            :connectionId
      {user-uuid :principalId} :authorizer} :requestContext} :datomic.ion.edn.api-gateway/data}]
  (let [{:keys [action message]} (read-string body)]
    (dispatch-message {:cid     connection-id
                       :uid     user-uuid
                       :action  action
                       :message message})))

(def handle-message-proxy (ionize handle-message))