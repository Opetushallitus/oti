(ns oti.util.session
  (:require [ring.middleware.session :as rms]
            [ring.middleware.session.cookie :as rmsc]))

(defn wrap-session [handler session-key]
  (rms/wrap-session handler {:store (rmsc/cookie-store {:key session-key})
                             :cookie-attrs {:max-age 60}}))
