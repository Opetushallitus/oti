(ns oti.ui.scoring.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :exam-sessions-full
 (fn [db _]
   (get-in db [:scoring :exam-sessions])))

(rf/reg-sub
 :selected-exam-session
 (fn [db _]
   (let [selected (get-in db [:scoring :selected-exam-session])]
     (if (nil? selected)
       (or (->> (get-in db [:scoring :exam-sessions])
                first
                :id)
           nil) ;; if no exam-sessions choose none
       selected))))

(rf/reg-sub
 :participants
 (fn [db _ [exam-session-id]]
  (->> (get-in db [:scoring :exam-sessions])
        (filter #(= (:id %) exam-session-id))
        first
        :participants
        (sort-by :last-name))))

(rf/reg-sub
 :selected-participant
 (fn [db _ [exam-session-id]]
   (get-in db [:scoring :selected-participant])))

(rf/reg-sub
 :participant
 (fn [db _ [exam-session-id participant-id]]
   (->> (get-in db [:scoring :exam-sessions])
        (filter #(= (:id %) exam-session-id))
        first
        :participants
        (filter #(= (:id %) participant-id))
        first)))


(rf/reg-sub
 :scoring-form-data
 (fn [db _ [exam-session-id participant-id]]
   (get-in db [:scoring :form-data exam-session-id participant-id])))

(rf/reg-sub
 :initial-form-data
 (fn [db _ [exam-session-id participant-id]]
   (get-in db [:scoring :initial-form-data exam-session-id participant-id])))
