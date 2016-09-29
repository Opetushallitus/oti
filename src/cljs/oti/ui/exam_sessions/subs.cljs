(ns oti.ui.exam-sessions.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  :exam-sessions
  (fn [db _]
    (:exam-sessions db)))
