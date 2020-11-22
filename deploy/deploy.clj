(ns deploy
  (:require
    [datomic.ion.dev :as dev]
    [clojure.java.io :as io]
    [taoensso.timbre :as timbre :refer [infof]]
    ))

(defn check-status-loop [{arn :execution-arn :as args}]
  (let [{:keys [deploy-status code-deploy-status] :as status}
        (dev/deploy-status {:op            :deploy-status
                            :execution-arn arn})]
    (if (contains? (set [deploy-status code-deploy-status]) "RUNNING")
      (do
        (infof "Deployment in progress...")
        @(future (Thread/sleep 3000) (check-status-loop args)))
      status)))

(defn push []
  (dev/push {:op :push}))

(defn deploy [{:keys [rev deploy-groups]}]
  (let [execution-arns (for [group deploy-groups]
                         (dev/deploy {:op    :deploy
                                      :group group
                                      :rev   rev}))]
    (let [deployment-results (map check-status-loop execution-arns)]
      (doall (map (fn [r] (infof "Deployment Complete: %s" r)) deployment-results))
      (System/exit 0))))

(defn -main [& [deploy-group]]
  (infof "Pushing to Code Deploy")
  (let [{:keys [rev deploy-groups dependency-conflicts] :as push-result} (push)]
    (infof "Deploying revision %s" rev)
    (deploy (assoc push-result :deploy-groups deploy-groups))))