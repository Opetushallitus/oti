(ns oti.ui.exam-registrations.subs
  (:require [re-frame.core :as re-frame]))

(def interesting-keys [:registrations])

(doseq [key interesting-keys]
  (re-frame/reg-sub
    key
    (fn [db _]
      (key db))))
