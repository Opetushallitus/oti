(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]))

(defn virkailija-endpoint [{:keys [db]}]
  (-> (context "/oti/api/virkailija" []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])})
        (context "/exam-sessions" []
          (POST "/" {params :params}
            (if (s/valid? :oti.spec/exam-session params)
              {:status 200}
              {:status 400
               :body {:errors (s/explain :oti.spec/exam-session params)}}))))
      (wrap-routes auth/wrap-authorization)))
