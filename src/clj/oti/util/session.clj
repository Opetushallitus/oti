(ns oti.util.session
  (:require [ring.middleware.session :as rms]
            [ring.middleware.session.cookie :as rmsc]))

(defn wrap-session [handler {:keys [key cookie-attrs]}]
  (rms/wrap-session handler {:store (rmsc/cookie-store {:key key})
                             :cookie-name "oti-session"
                             :cookie-attrs cookie-attrs}))
