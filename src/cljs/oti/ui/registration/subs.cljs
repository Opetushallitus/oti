(ns oti.ui.registration.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  :language
  (fn [db _]
    (:language db)))
