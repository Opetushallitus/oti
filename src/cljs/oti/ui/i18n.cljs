(ns oti.ui.i18n
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  :translations
  (fn [db _]
    (:translations db)))

(defn t [key & [default]]
  (let [translations (re-frame/subscribe [:translations])]
    (if-not (empty? @translations)
      (let [k (if (keyword? key) (name key) key)]
        (get @translations k k))
      (or default ""))))

(def pikaday-i18n
  {:previous-month "Edellinen kuukausi"
   :next-month "Seuraava kuukausi",
   :months ["Tammikuu" "Helmikuu" "Maaliskuu" "Huhtikuu" "Toukokuu" "Kesäkuu" "Heinäkuu" "Elokuu" "Syyskuu" "Lokakuu" "Marraskuu" "Joulukuu"]
   :weekdays ["Sunnuntai" "Maanantai" "Tiistai" "Keskiviikko" "Torstai" "Perjantai" "Lauantai"]
   :weekdays-short ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La"]})
