(ns oti.ui.registration.subs
  (:require [re-frame.core :as re-frame]))

(def interesting-keys [:language :participant-data])

(doseq [key interesting-keys]
  (re-frame/reg-sub
    key
    (fn [db _]
      (key db))))
