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
          (POST "/" {exam-data :body}
            (if (s/valid? :oti.spec/exam-session exam-data)
              {:status 200}
              {:status 400}))))
      (wrap-routes auth/wrap-authorization)))
