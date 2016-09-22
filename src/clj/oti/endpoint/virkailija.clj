(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect]]
            [oti.util.auth :as auth]))

(defn virkailija-endpoint [{{db :spec} :db}]
  (-> (context "/oti/api/virkailija" []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])}))
      (wrap-routes auth/wrap-authorization)))
