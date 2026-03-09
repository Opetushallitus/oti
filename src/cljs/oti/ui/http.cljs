(ns oti.ui.http
  (:import (goog.net Cookies)))

(def cks (-> js/document Cookies.))

(defn http-default-headers []
  {:Caller-Id "1.2.246.562.10.00000000001.oti"})

(defn csrf-header [] {"CSRF" (or (.get cks "CSRF") (.get cks "csrf") "CSRF")})
