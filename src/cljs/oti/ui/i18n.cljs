(ns oti.ui.i18n
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  :translations
  (fn [db _]
    (:translations db)))

(defn t [key]
  (let [translations (re-frame/subscribe [:translations])]
    (println @translations)
    (get @translations key key)))
