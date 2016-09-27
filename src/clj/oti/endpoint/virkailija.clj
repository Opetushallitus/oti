(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]))

(defn- convert-session-row [{:keys [id session_date start_time end_time street_address city other_location_info max_participants]}]
  #:oti.spec{:id id
             :session-date session_date
             :start-time (str (.toLocalTime start_time))
             :end-time (str (.toLocalTime end_time))
             :street-address street_address
             :city city
             :other-location-info other_location_info
             :max-participants max_participants})

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
                (response {:success true}))))
          (GET "/" []
            (let [sessions (->> (dba/upcoming-exam-sessions db)
                                (map convert-session-row))]
              (response sessions)))))
      (wrap-routes auth/wrap-authorization)))
