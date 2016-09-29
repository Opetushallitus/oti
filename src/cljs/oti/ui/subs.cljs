(ns oti.ui.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :user
 (fn [db]
   (:user db)))

(re-frame/reg-sub
 :active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
  :flash-message
  (fn [db _]
    (:flash-message db)))