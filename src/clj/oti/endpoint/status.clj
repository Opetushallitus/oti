(ns oti.endpoint.status
  (:require [compojure.core :refer :all]
            [clojure.java.io :as io]
            [ring.util.response :as res]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [clojure.tools.logging :as log]))

(defn- read-resource [res]
  (try
    (-> (io/resource res)
        (slurp)
        (str/trim-newline))
    (catch Throwable t
      (log/error t))))

(defn status-endpoint [{:keys [db]}]
  (context "/oti" []
    (GET "/version" []
      (res/response {:status "OK"}))
    (GET "/health" []
      (let [ok? (dba/health-check db)]
        (if ok?
          (res/response {:status "OK"})
          {:status 500 :body {:status "ERROR"
                              :reason "Database connection error"}})))))
