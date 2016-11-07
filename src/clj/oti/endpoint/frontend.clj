(ns oti.endpoint.frontend
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect header]]
            [oti.util.auth :as auth]
            [oti.component.url-helper :refer [url]]
            [ring.util.mime-type :as mime]))

(defn index-response [participant?]
  (let [index (if participant? "participant-index.html" "virkailija-index.html")]
    (-> (resource-response (str "/oti/public/" index))
        (content-type "text/html; charset=utf-8"))))

(defn- add-mime-type [response path]
  (if-let [mime-type (mime/ext-mime-type path {"js" "text/javascript; charset=utf-8"})]
    (content-type response mime-type)
    response))

(defn frontend-endpoint [{:keys [url-helper]}]
  (routes
    (GET "/" []
      (redirect "/oti/virkailija"))
    (context "/oti" []
      (GET "/" []
        (redirect "/oti/virkailija"))
      (-> (GET "/virkailija*" []
            (index-response false))
          (wrap-routes auth/wrap-authorization :redirect-uri (url url-helper "oti.cas-auth")))
      (GET "/ilmoittaudu*" []
        (index-response true))
      (GET "/anmala*" []
        (index-response true))
      (GET "/*" {{resource-path :*} :route-params}
        (let [root "/oti/public"]
          (some-> (resource-response (str root "/" resource-path))
                  (add-mime-type resource-path)
                  (header "Cache-Control" "max-age=120")))))))
