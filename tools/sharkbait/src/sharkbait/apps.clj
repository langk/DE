(ns sharkbait.apps
  (:require [clojure.string :as string]
            [sharkbait.consts :as consts]
            [sharkbait.db :as db]
            [sharkbait.permissions :as perms]
            [sharkbait.roles :as roles]))

(defn- create-public-apps-resource
  "Creates the resource that all public apps inherit their permissions from."
  [session permission-def folder-name]
  (perms/create-permission-resource session permission-def folder-name consts/public-apps-resource-name)
  (perms/grant-permission (roles/find-role session consts/full-de-users-role-name) perms/read permission-def))

(defn- extract-username
  "Extracts the username of an owner from an app."
  [app]
  (->> (into [] (.getArray (:users app)))
       (filter (partial re-find #"@iplantcollaborative.org$"))
       (first)))

(defn- find-app-owner-subject
  "Finds the subject corresponding to the owner of an app."
  [subjects app]
  (when-let [username (extract-username app)]
    (subjects (string/replace username #"@iplantcollaborative.org$" ""))))

(defn- find-app-owner-membership
  [subjects app]
  (when-let [subject (find-app-owner-subject subjects app)]
    (roles/find-membership consts/full-de-users-role-name subject)))

(defn- grant-owner-permission
  "Grants ownership permission to an app."
  [subjects app-resource app]
  (when-let [membership (find-app-owner-membership subjects app)]
    (perms/grant-permission membership perms/own app-resource)))

(defn- register-app
  [session subjects permission-def public-apps-resource folder-name app]
  (let [app-resource (perms/create-permission-resource session permission-def folder-name (:id app))]
    (if (:is_public app)
      (perms/add-permission-name-implication public-apps-resource app-resource)
      (grant-owner-permission subjects app-resource app))))

(defn register-de-apps
  "Registers all DE apps in Grouper."
  [database session subjects folder-name permission-def-name]
  (let [permission-def       (perms/find-permission-def folder-name permission-def-name)
        public-apps-resource (create-public-apps-resource session permission-def folder-name)]
    (dorun (map (partial register-app session subjects permission-def public-apps-resource folder-name)
                (db/list-de-apps database)))))
