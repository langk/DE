(ns clj-icat-direct.queries
  (:require [clojure.string :as string])
  (:import  [clojure.lang ISeq]))


(defn- get-folder-like-clause
  "Returns a LIKE clause for folder paths in the count-filtered-items-in-folder query."
  []
  "c.coll_name LIKE ?")

(defn- get-folder-path-clause
  "Returns an equals clause for folder paths in the count-filtered-items-in-folder query."
  []
  "c.coll_name = ?")

(defn get-filtered-paths-where-clause
  "Returns a LIKE clause for folder paths in the count-filtered-items-in-folder query."
  [filter-files filtered-paths]
  (let [clauses (concat (repeat (count filter-files) (get-folder-like-clause))
                        (repeat (count filtered-paths) (get-folder-path-clause)))]
    (if (empty? clauses)
      "FALSE"
      (string/join " OR " clauses))))


(defn filter-files->query-args
  "Converts the list of filter-files for use as arguments in the count-filtered-items-in-folder
   query."
  [filter-files]
  (map (partial str "%/") filter-files))

(defn filter-chars->sql-char-class
  "Returns a regex character class set for use in the count-filtered-items-in-folder query
   using the given characters."
  [filter-chars]
  (if (empty? filter-chars)
    "^$"
    (str "["
         (string/replace filter-chars
                         #"'|\[|\]|\\"
                         {"\\" "\\\\"
                          "'" "\\'"
                          "[" "\\["
                          "]" "\\]"})
         "]")))

(defn prepare-text-set
  "Given a set, it prepares the elements for injection into an SQL query. It returns a string
   containing the quoted values separated by commas."
  [values]
  (string/join ", " (map #(str \' % \') values)))


(defn ^String mk-file-type-join
  "This function constructs the file type join for the paged folder listing query.

   Parameters:
     file-types - A list of file types used to filter on. A nil or empty list disables filtering.

   Returns:
     It returns a join clause to be added as the first format parameter in the paged-folder-listing
     query template."
  [^ISeq file-types]
  (let [file-types       (mapv string/lower-case file-types)
        fmt-ft           (prepare-text-set file-types)
        [join-type filt] (cond
                           (empty? file-types)
                           ["LEFT" ""]

                           (contains? file-types "raw")
                           ["LEFT" (str "AND meta_attr_value IN ('', " fmt-ft ")")]

                           :else
                           ["INNER" (str "AND meta_attr_value IN (" fmt-ft ")")])]
    (str join-type " JOIN (SELECT * FROM file_avus WHERE meta_attr_name = 'ipc-filetype' " filt ")
                          AS f
           ON f.object_id = d.data_id")))


(def queries
  {:count-items-in-folder
   "WITH user_groups AS ( SELECT g.*
                            FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ? ),

         data_objs   AS ( SELECT *
                            FROM r_data_main
                           WHERE coll_id = ANY(ARRAY( SELECT coll_id FROM parent )))

    SELECT count(*) AS total
      FROM ( SELECT DISTINCT d.data_id FROM r_objt_access a
               JOIN data_objs d ON a.object_id = d.data_id
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND a.object_id IN ( SELECT data_id from data_objs )
              UNION
             SELECT DISTINCT c.coll_id FROM r_coll_main c
               JOIN r_objt_access a ON c.coll_id = a.object_id
               JOIN parent p ON c.parent_coll_name = p.coll_name
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND c.coll_type != 'linkPoint' ) AS contents"

   :count-all-items-under-folder
   "WITH user_groups AS ( SELECT g.*
                            FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ?
                          UNION
                          SELECT * from r_coll_main
                           WHERE coll_name LIKE ? || '/%' ),

         data_objs   AS ( SELECT *
                            FROM r_data_main
                           WHERE coll_id = ANY(ARRAY( SELECT coll_id FROM parent )))

    SELECT count(*) AS total
      FROM ( SELECT DISTINCT d.data_id FROM r_objt_access a
               JOIN data_objs d ON a.object_id = d.data_id
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND a.object_id IN ( SELECT data_id from data_objs )
              UNION
             SELECT DISTINCT c.coll_id FROM r_coll_main c
               JOIN r_objt_access a ON c.coll_id = a.object_id
               JOIN parent p ON c.parent_coll_name = p.coll_name
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND c.coll_type != 'linkPoint' ) AS contents"

   :count-filtered-items-in-folder
   "WITH user_groups AS ( SELECT g.* FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ? ),

         data_objs   AS ( SELECT *
                            FROM r_data_main
                           WHERE coll_id = ANY(ARRAY( SELECT coll_id FROM parent )))

    SELECT count(*) AS total_filtered
      FROM ( SELECT DISTINCT d.data_id FROM r_objt_access a
               JOIN data_objs d ON a.object_id = d.data_id
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND a.object_id IN ( SELECT data_id FROM data_objs )
                AND d.data_name ~ ?
              UNION
             SELECT DISTINCT c.coll_id FROM r_coll_main c
               JOIN r_objt_access a ON c.coll_id = a.object_id
               JOIN parent p ON c.parent_coll_name = p.coll_name
              WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                AND c.coll_type != 'linkPoint'
                AND (%s)) AS filtered"

   :list-folders-in-folder
   "WITH user_groups AS ( SELECT g.* FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ? )

    SELECT DISTINCT
           c.parent_coll_name                     as dir_name,
           c.coll_name                            as full_path,
           regexp_replace(c.coll_name, '.*/', '') as base_name,
           c.create_ts                            as create_ts,
           c.modify_ts                            as modify_ts,
           'collection'                           as type,
           0                                      as data_size,
           m.meta_attr_value                      as uuid,
           MAX(a.access_type_id)                  as access_type_id
      FROM r_coll_main c
      JOIN r_objt_access a ON c.coll_id = a.object_id
      JOIN parent p ON c.parent_coll_name = p.coll_name
      JOIN r_objt_metamap mm ON mm.object_id = c.coll_id
      JOIN r_meta_main m ON m.meta_id = mm.meta_id 
     WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
       AND c.coll_type != 'linkPoint'
       AND m.meta_attr_name = 'ipc_UUID'
  GROUP BY dir_name, full_path, base_name, c.create_ts, c.modify_ts, type, data_size, uuid
  ORDER BY base_name ASC"

   :count-files-in-folder
   "WITH user_groups AS ( SELECT g.*
                            FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ? ),

         data_objs   AS ( SELECT *
                            FROM r_data_main
                           WHERE coll_id = ANY(ARRAY( SELECT coll_id FROM parent )))

      SELECT count(DISTINCT d.data_id) FROM r_objt_access a
        JOIN data_objs d ON a.object_id = d.data_id
       WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
         AND a.object_id IN ( SELECT data_id from data_objs )"

   :count-folders-in-folder
   "WITH user_groups AS ( SELECT g.*
                            FROM r_user_main u
                            JOIN r_user_group g ON g.user_id = u.user_id
                           WHERE u.user_name = ?
                             AND u.zone_name = ? ),

         parent      AS ( SELECT * from r_coll_main
                           WHERE coll_name = ? )

    SELECT count(DISTINCT c.coll_id) FROM r_coll_main c
      JOIN r_objt_access a ON c.coll_id = a.object_id
      JOIN parent p ON c.parent_coll_name = p.coll_name
     WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
       AND c.coll_type != 'linkPoint'"

   :file-permissions
   "SELECT DISTINCT o.access_type_id, u.user_name
      FROM r_user_main u,
           r_data_main d,
           r_coll_main c,
           r_tokn_main t,
           r_objt_access o
     WHERE c.coll_name = ?
       AND d.data_name = ?
       AND c.coll_id = d.coll_id
       AND o.object_id = d.data_id
       AND t.token_namespace = 'access_type'
       AND u.user_id = o.user_id
       AND o.access_type_id = t.token_id
     LIMIT ?
    OFFSET ?"

   :folder-permissions
   "SELECT DISTINCT a.access_type_id, u.user_name
     FROM r_coll_main c
     JOIN r_objt_access a ON c.coll_id = a.object_id
     JOIN r_user_main u ON a.user_id = u.user_id
    WHERE c.parent_coll_name = ?
      AND c.coll_name = ?
    LIMIT ?
   OFFSET ?"

   :folder-permissions-for-user
   "WITH user_lookup AS ( SELECT u.user_id as user_id FROM r_user_main u WHERE u.user_name = ?)
    SELECT DISTINCT a.access_type_id
      FROM r_coll_main c
      JOIN r_objt_access a ON c.coll_id = a.object_id
      JOIN r_user_main u ON a.user_id = u.user_id
     WHERE c.coll_name = ?
       AND u.user_id IN ( SELECT g.group_user_id
                           FROM  r_user_group g,
                                 user_lookup
                           WHERE g.user_id = user_lookup.user_id )"

   :file-permissions-for-user
   "WITH user_lookup AS ( SELECT u.user_id as user_id FROM r_user_main u WHERE u.user_name = ? ),
              parent AS ( SELECT c.coll_id as coll_id, c.coll_name as coll_name FROM r_coll_main c WHERE c.coll_name = ? )
    SELECT DISTINCT a.access_type_id
      FROM r_data_main d
      JOIN r_coll_main c ON c.coll_id = d.coll_id
      JOIN r_objt_access a ON d.data_id = a.object_id
      JOIN r_user_main u ON a.user_id = u.user_id,
           user_lookup,
           parent
     WHERE u.user_id IN ( SELECT g.group_user_id
                           FROM  r_user_group g,
                                 user_lookup
                           WHERE g.user_id = user_lookup.user_id )
       AND c.coll_id = parent.coll_id
       AND d.data_name = ?"

   :folder-listing
   "WITH user_access AS (SELECT *
                           FROM r_objt_access
                           WHERE user_id IN (SELECT g.group_user_id
                                               FROM r_user_main u
                                                 JOIN r_user_group g ON g.user_id = u.user_id
                                               WHERE u.user_name = ? AND u.zone_name = ?)),
         parent      AS (SELECT * FROM r_coll_main WHERE coll_name = ?)
    SELECT DISTINCT c.coll_name || '/' || d.data_name AS full_path
      FROM r_data_main d JOIN r_coll_main c ON d.coll_id = c.coll_id
      WHERE c.coll_name IN (SELECT coll_name FROM parent)
        AND d.data_id IN (SELECT object_id FROM user_access)
    UNION
    SELECT coll_name AS full_path
      FROM r_coll_main
      WHERE parent_coll_name IN (SELECT coll_name FROM parent)
        AND coll_id IN (SELECT object_id FROM user_access)
        AND coll_type != 'linkPoint'"

   :paged-folder-listing
   "WITH user_groups AS ( SELECT g.*
                        FROM r_user_main AS u JOIN r_user_group AS g ON g.user_id = u.user_id
                        WHERE u.user_name = ? AND u.zone_name = ? ),

         parent      AS ( SELECT * FROM r_coll_main WHERE coll_name = ? ),

         data_objs   AS ( SELECT *
                            FROM r_data_main d1
                            WHERE coll_id = ANY(ARRAY( SELECT coll_id FROM parent ))
                              AND d1.data_repl_num = ( SELECT MIN(d2.data_repl_num)
                                                         FROM r_data_main AS d2
                                                         WHERE d2.data_id = d1.data_id )),

         file_avus   AS ( SELECT *
                            FROM r_objt_metamap om JOIN r_meta_main mm ON mm.meta_id = om.meta_id
                            WHERE om.object_id = ANY(ARRAY( SELECT data_id FROM data_objs )))

    SELECT *
      FROM ( SELECT 'collection'                           AS type,
                    m.meta_attr_value                      AS uuid,
                    c.coll_name                            AS full_path,
                    regexp_replace(c.coll_name, '.*/', '') AS base_name,
                    NULL                                   AS info_type,
                    0                                      AS data_size,
                    c.create_ts                            AS create_ts,
                    c.modify_ts                            AS modify_ts,
                    a.access_type_id                       AS access_type_id
               FROM r_coll_main c
                 JOIN r_objt_access a ON c.coll_id = a.object_id
                 JOIN parent p ON c.parent_coll_name = p.coll_name
                 JOIN r_objt_metamap om ON om.object_id = c.coll_id
                 JOIN r_meta_main m ON m.meta_id = om.meta_id
               WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                 AND c.coll_type != 'linkPoint'
                 AND m.meta_attr_name = 'ipc_UUID'
             UNION
             SELECT 'dataobject'                      AS type,
                    m.meta_attr_value                 AS uuid,
                    c.coll_name || '/' || d.data_name AS full_path,
                    d.data_name                       AS base_name,
                    f.meta_attr_value                 AS info_type,
                    d.data_size                       AS data_size,
                    d.create_ts                       AS create_ts,
                    d.modify_ts                       AS modify_ts,
                    a.access_type_id                  AS access_type_id
               FROM r_objt_access a
                 JOIN data_objs d ON a.object_id = d.data_id
                 JOIN r_coll_main c ON c.coll_id = d.coll_id
                 JOIN file_avus m ON m.object_id = d.data_id
                 %s
                 WHERE a.user_id IN ( SELECT group_user_id FROM user_groups )
                   AND m.meta_attr_name = 'ipc_UUID' ) AS p
      ORDER BY p.type ASC, %s %s
      LIMIT ?
      OFFSET ?"

   :select-files-with-uuids
   "SELECT DISTINCT m.meta_attr_value                   uuid,
                    (c.coll_name || '/' || d.data_name) path,
                    1000 * CAST(d.create_ts AS BIGINT)  \"date-created\",
                    1000 * CAST(d.modify_ts AS BIGINT)  \"date-modified\",
                    d.data_size                         \"file-size\"
      FROM r_meta_main m
        JOIN r_objt_metamap o ON m.meta_id = o.meta_id
        JOIN r_data_main d ON o.object_id = d.data_id
        JOIN r_coll_main c ON d.coll_id = c.coll_id
      WHERE m.meta_attr_name = 'ipc_UUID' AND m.meta_attr_value IN (%s)"

   :select-folders-with-uuids
   "SELECT m.meta_attr_value                  uuid,
           c.coll_name                        path,
           1000 * CAST(c.create_ts AS BIGINT) \"date-created\",
           1000 * CAST(c.modify_ts AS BIGINT) \"date-modified\"
      FROM r_meta_main m
        JOIN r_objt_metamap o ON m.meta_id = o.meta_id
        JOIN r_coll_main c ON o.object_id = c.coll_id
      WHERE m.meta_attr_name = 'ipc_UUID' AND m.meta_attr_value IN (%s)"

   :paged-uuid-listing
   "WITH groups AS (SELECT group_user_id
                      FROM r_user_group
                      WHERE user_id IN (SELECT user_id
                                          FROM r_user_main
                                          WHERE user_name = ? AND zone_name = ?)),
         uuids AS (SELECT m.meta_attr_value uuid,
                          o.object_id
                     FROM r_meta_main m JOIN r_objt_metamap o ON m.meta_id = o.meta_id
                     WHERE m.meta_attr_name = 'ipc_UUID'
                       AND m.meta_attr_value IN (%s)
                       AND o.object_id IN (SELECT object_id
                                             FROM r_objt_access
                                             WHERE user_id in (SELECT group_user_id FROM groups)))
    SELECT *
      FROM (SELECT c.coll_name                            AS full_path,
                   regexp_replace(c.coll_name, '.*/', '') AS base_name,
                   0                                      AS data_size,
                   c.create_ts                            AS create_ts,
                   c.modify_ts                            AS modify_ts,
                   u.uuid                                 AS uuid,
                   'collection'                           AS type
                   FROM uuids u JOIN r_coll_main c ON u.object_id = c.coll_id
                   WHERE c.coll_type != 'linkPoint'
            UNION
            SELECT (c.coll_name || '/' || d.data_name) AS full_path,
                   d.data_name                         AS base_name,
                   (array_agg(d.data_size))[1]         AS data_size,
                   (array_agg(d.create_ts))[1]         AS create_ts,
                   (array_agg(d.modify_ts))[1]         AS modify_ts,
                   u.uuid                              AS uuid,
                   'dataobject'                        AS type
              FROM uuids u
                JOIN r_data_main d ON u.object_id = d.data_id
                JOIN r_coll_main c ON d.coll_id = c.coll_id
              GROUP BY full_path, base_name, uuid) p
      ORDER BY type ASC, %s %s
      LIMIT ?
      OFFSET ?"})