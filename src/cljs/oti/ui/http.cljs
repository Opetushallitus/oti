(ns oti.http (:require [goog.net.cookies :as cookies]))

(defn http-default-headers []
  {:Caller-Id "1.2.246.562.10.00000000001.oti"})

(defn csrf-header [] {"CSRF" (or (cookies/get "CSRF") (cookies/get "csrf") "CSRF")})
