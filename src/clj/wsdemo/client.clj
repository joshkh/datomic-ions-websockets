(ns wsdemo.client
  (:require [clojure.java.io :as io]
            [datomic.client.api :as d]))

(def config (-> "project-config.edn" io/resource slurp read-string))

(def get-client
  (fn []
    (try
      (d/client (:connection config))
      (catch Exception e (println (ex-data e))))))

(defn get-connection*
  "Get a new connection to a database"
  []
  (d/connect (get-client) {:db-name (:db-name config)}))

(def get-conn get-connection*)

(defn db
  "Returns the most recent database from the connection"
  []
  (d/db (get-conn)))
