(ns oti.ui.views.common
  (:require [oti.ui.i18n :refer [t]]))

(defn loader [loading?]
  (when (or (nil? loading?) loading?)
    [:div.loader "Ladataan"]))

(defn flash-message [flash-opts]
  (when (seq flash-opts)
    (let [{:keys [type text]} flash-opts]
      [:div.flash-container
       [:div.flash-message
        [:span.icon {:class (if (= :success type) "success" "error")}
         (if (= :success type)
           "\u2713"
           "\u26A0")]
        [:span.text text]]])))
