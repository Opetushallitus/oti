(ns oti.ui.views.common
  (:require [oti.ui.i18n :refer [t]]))

(defn loader [loading?]
  (when (or (nil? loading?) loading?)
    [:div.loader "Ladataan"]))
