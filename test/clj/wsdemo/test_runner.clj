(ns wsdemo.test-runner
  (:require [clojure.test :as t :refer [deftest is testing]]
            [wsdemo.test-connections]))

(defn -main []
  (t/run-tests 'wsdemo.test-connections))
