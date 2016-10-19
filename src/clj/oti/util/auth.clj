(ns oti.util.auth
  (:require [ring.util.response :as resp])
  (:import [java.net URLEncoder]))

(defonce cas-tickets (atom #{}))

(defn login [ticket]
  (swap! cas-tickets conj ticket))

(defn logout [ticket]
  (swap! cas-tickets disj ticket))

(defn logged-in? [request]
  (let [ticket (-> request :session :identity :ticket)]
    (contains? @cas-tickets ticket)))

(defn wrap-authorization [handler & {:keys [redirect-uri]}]
  (fn [request]
    (cond
      (logged-in? request) (handler request)
      redirect-uri (resp/redirect (str redirect-uri "?path=" (URLEncoder/encode (:uri request))))
      :else {:status 401 :body "Unauthorized" :headers {"Content-Type" "text/plain; charset=utf-8"}})))
