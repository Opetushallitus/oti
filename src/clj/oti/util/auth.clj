(ns oti.util.auth
  (:require [ring.util.response :as resp]))

(defonce cas-tickets (atom #{}))

(defn login [ticket]
  (swap! cas-tickets conj ticket))

(defn logout [ticket]
  (swap! cas-tickets disj ticket))

(defn logged-in? [request]
  (let [ticket (-> request :session :identity :ticket)]
    (contains? @cas-tickets ticket)))

(defn wrap-authorization [handler & [redirect?]]
  (fn [request]
    (cond
      (logged-in? request) (handler request)
      redirect? (resp/redirect "/oti/auth/cas")
      :else {:status 401 :body "Unauthorized" :headers {"Content-Type" "text/plain; charset=utf-8"}})))
