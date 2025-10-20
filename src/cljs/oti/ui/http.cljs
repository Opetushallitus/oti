(ns oti.http (:require [goog.net.Cookies :as cookies]))

(def cks (cookies/getInstance))

(defn http-default-headers []
  {:Caller-Id "1.2.246.562.10.00000000001.oti"})

(defn csrf-header [] {"CSRF" (or (.get cks "CSRF") (.get cks "csrf") "CSRF")})
