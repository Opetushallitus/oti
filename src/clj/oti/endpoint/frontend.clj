(ns oti.endpoint.frontend
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect header]]
            [oti.util.auth :as auth]
            [oti.component.url-helper :refer [url]]
            [ring.util.mime-type :as mime]
            [selmer.parser :as selmer]
            [clojure.tools.logging :as log]))

(defn index-response [participant? {:keys [env]}]
  (let [index (if participant? "participant-index.html" "virkailija-index.html")]
    (selmer/render-file (str "oti/html-templates/" index) {:env env})))

(defn- add-mime-type [response path]
  (if-let [mime-type (mime/ext-mime-type path {"js" "text/javascript; charset=utf-8"})]
    (content-type response mime-type)
    response))

(defn frontend-endpoint [{:keys [url-helper global-config]}]
  (routes
    (GET "/" []
      (redirect "/oti/virkailija"))
    (context "/oti" []
      (GET "/" []
        (redirect "/oti/virkailija"))
      (-> (GET "/virkailija*" []
            (index-response false global-config))
          (wrap-routes auth/wrap-authorization :redirect-uri (url url-helper "oti.cas-auth")))
      (GET "/ilmoittaudu*" []
           (index-response true global-config))
      (GET "/anmala*" []
        (index-response true global-config))
      (GET "/*" {{resource-path :*} :route-params}
        (let [root "/oti/public"]
          (some-> (resource-response (str root "/" resource-path))
                  (add-mime-type resource-path)
                  (header "Cache-Control" "max-age=120")))))))
