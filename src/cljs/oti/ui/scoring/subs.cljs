(ns oti.ui.scoring.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :exam-sessions-full
 (fn [db _]
   (vals (get-in db [:scoring :exam-sessions]))))

(rf/reg-sub
 :selected-exam-session
 (fn [db _]
   (let [selected (get-in db [:scoring :selected-exam-session])]
     (if (nil? selected)
       (or (->> (get-in db [:scoring :exam-sessions])
                first
                second
                :id)
           nil) ;; if no exam-sessions choose none
       selected))))

(rf/reg-sub
 :participants
 (fn [db _ [exam-session-id]]
  (->> (get-in db [:scoring :exam-sessions exam-session-id])
       :participants
       vals
       (sort-by :last-name))))

(rf/reg-sub
 :selected-participant
 (fn [db _ [exam-session-id]]
   (get-in db [:scoring :selected-participant])))

(rf/reg-sub
 :current-participant-form-data
 (fn [db]
   (let [sp (get-in db [:scoring :selected-participant])
         se (get-in db [:scoring :selected-exam-session])]
     (get-in db [:scoring :form-data se sp]))))

(rf/reg-sub
 :scoring-form-data
 (fn [db _ [exam-session-id participant-id]]
   (get-in db [:scoring :form-data exam-session-id participant-id])))

(rf/reg-sub
 :initial-form-data
 (fn [db _ [exam-session-id participant-id]]
   (get-in db [:scoring :initial-form-data exam-session-id participant-id])))

(rf/reg-sub
 :email-sent?
 (fn [db _ [exam-session-id participant-id]]
   (get-in db [:scoring :emails-sent exam-session-id participant-id])))
