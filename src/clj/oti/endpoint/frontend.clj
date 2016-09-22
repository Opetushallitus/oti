(ns oti.endpoint.frontend
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response content-type redirect]]
            [oti.util.auth :as auth]))

(defn frontend-endpoint [_]
  (routes
    (GET "/" []
      (redirect "/oti/virkailija"))
    (context "/oti" []
      (-> (GET "/virkailija*" []
            (-> (resource-response "/oti/public/index.html")
                (content-type "text/html; charset=utf-8")))
          (wrap-routes auth/wrap-authorization :redirect))
      (-> (route/resources "/" {:root "/oti/public"
                                :mime-types {"js" "text/javascript; charset=utf-8"}})
          (wrap-routes auth/wrap-authorization)))))
