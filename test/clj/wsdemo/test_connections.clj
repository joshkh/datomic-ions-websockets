(ns wsdemo.test-connections
  (:require [clojure.test :refer [deftest is testing run-tests use-fixtures]]
            [gniazdo.core :as ws]
            [clojure.java.io :as io]
            [buddy.sign.jwt :as jwt])
  (:import (java.util UUID)))


(def config (-> "project-config.edn" io/resource slurp read-string))

(def ws-url (let [{:keys [hostname stage]} (:api-gw config)] (str "wss://" hostname "/" stage)))

(def secret (:secret config))

(def conn-1 (ws/connect ws-url
              :headers {"Authorization" (jwt/sign {:sub (UUID/randomUUID)} secret)}
              :on-connect (fn [e] (println "Connected:" e))
              :on-receive (fn [e] (println "Message received:" (read-string e)))
              :on-error (fn [e] (println "Error:" e))))

(def conn-2 (ws/connect ws-url
              :headers {"Authorization" (jwt/sign {:sub (UUID/randomUUID)} secret)}
              :on-connect (fn [e] (println "Connected:" e))
              :on-receive (fn [e] (println "Message received:" (read-string e)))
              :on-error (fn [e] (println "Error:" e))))

(defn broadcast-from-conn-1 []
  (ws/send-msg conn-1 (prn-str {:action :broadcast :message "Hello everyone"})))