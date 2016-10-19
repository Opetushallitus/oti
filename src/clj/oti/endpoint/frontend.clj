(ns oti.endpoint.frontend
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response content-type redirect header]]
            [oti.util.auth :as auth]))

(defn index-response [participant?]
  (let [index (if participant? "participant-index.html" "virkailija-index.html")]
    (-> (resource-response (str "/oti/public/" index))
        (content-type "text/html; charset=utf-8")
        (header "Cache-Control" "no-store, must-revalidate"))))

(defn frontend-endpoint [{:keys [authentication]}]
  (routes
    (GET "/" []
      (redirect "/oti/virkailija"))
    (context "/oti" []
      (GET "/" []
        (redirect "/oti/virkailija"))
      (-> (GET "/virkailija*" []
            (index-response false))
          (wrap-routes auth/wrap-authorization :redirect-uri (:oti-login-success-uri authentication)))
      (GET "/ilmoittaudu*" []
        (index-response true))
      (GET "/anmala*" []
        (index-response true))
      (route/resources "/" {:root "/oti/public"
                            :mime-types {"js" "text/javascript; charset=utf-8"}}))))
