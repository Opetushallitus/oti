(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]))

(defn virkailija-endpoint [{:keys [db]}]
  (-> (context "/oti/api/virkailija" []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])})
        (context "/exam-sessions" []
          (POST "/" {params :params}
            (let [conformed (s/conform ::os/exam-session params)]
              (if (or (s/invalid? conformed) (not= 1 (dba/add-exam-session! db conformed)))
                {:status 400
                 :body {:errors (s/explain ::os/exam-session params)}}
                {:status 200})))))
      (wrap-routes auth/wrap-authorization)))
