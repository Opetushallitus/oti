(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.util.coercion :as c]))

(defn- as-int [x]
  (try (Integer/parseInt x)
       (catch Throwable _)))

(defn virkailija-endpoint [{:keys [db]}]
  (-> (context "/oti/api/virkailija" []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])})
        (context "/exam-sessions" []
          (POST "/" {params :params}
            (let [conformed (s/conform ::os/exam-session params)]
              (if (or (s/invalid? conformed) (not (seq (dba/add-exam-session! db conformed))))
                {:status 400
                 :body {:errors (s/explain ::os/exam-session params)}}
                (response {:success true}))))
          (GET "/" []
            (let [sessions (->> (dba/upcoming-exam-sessions db)
                                (map c/convert-session-row))]
              (response sessions)))
          (context "/:id{[0-9]+}" [id :<< as-int]
            (GET "/" []
              (if-let [exam-session (->> (dba/exam-session db id)
                                         (map c/convert-session-row)
                                         first)]
                (response exam-session)
                (not-found {})))
            (PUT "/" {params :params}
              (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))]
                (if (or (s/invalid? conformed) (not (seq (dba/save-exam-session! db conformed))))
                  {:status 400
                   :body {:errors (s/explain ::os/exam-session params)}}
                  (response {:success true})))))))
      (wrap-routes auth/wrap-authorization)))
