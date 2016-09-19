(ns oti.util.session
  (:require [ring.middleware.session :as rms]
            [ring.middleware.session.cookie :as rmsc]))

(defn wrap-session [handler session-key]
  (rms/wrap-session handler {:store (rmsc/cookie-store session-key)}))
