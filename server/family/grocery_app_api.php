<?php
/* ============================================================
   Grocery Accountant phone app — sign-in and sync.  PHP 5.6 compatible.
   /family/grocery_app_api.php        Needs schema_v27 (and v24–v26 before it).

   This used to be family/app_api.php, but the Family Manager app
   needs that file name too. Grocery Accountant now calls this file;
   app_api.php hands older Grocery Accountant versions over to it,
   so phones that haven't updated keep working.

   The phone works fully offline; signing in is optional. When it is
   signed in, it calls this file with its key in a header:
       Authorization: Bearer <key>      (and X-App-Token: <key>, because
                                          some hosts strip Authorization)

   Actions (JSON in, JSON out):
     POST ?action=login    {username, password, device}  -> {token, user}
     POST ?action=logout                                   -> ends this phone's key
     GET  ?action=whoami                                   -> {user}
     POST ?action=sync     {base, lists[], items[], benefits?}
          -> applies the phone's changes, then returns everything:
             {server_time, map{lists{cid:rid}, items{cid:rid}}, conflicts,
              snapshot{lists[{…, items[…]}], benefits, can_edit_benefits}}

   Conflicts: "base" is the server_time the phone got on its last sync.
   If a row was changed on the website after that, the website's version
   wins and the phone's change to that row is dropped (counted in
   "conflicts"). New rows from the phone are always added.
   ============================================================ */
@ini_set('display_errors', '0');
require_once __DIR__ . '/lib.php';
@ini_set('display_errors', '0');

header('Cache-Control: no-store');
$action = isset($_GET['action']) ? (string) $_GET['action'] : '';

/* Never answer with a bare 500: the phone shows "The website answered 500" and nobody
   can tell why. Errors go to the server's error log, and the phone gets a short reason. */
function app_fail($why)
{
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
    }
    echo json_encode(array('ok' => false, 'error' => 'Website error: ' . $why));
    exit;
}
set_exception_handler(function ($e) {
    error_log('grocery_app_api: ' . get_class($e) . ': ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    $msg = $e->getMessage();
    if (preg_match("/Table '[^']*\\.([a-z_]+)' doesn't exist/i", $msg, $m)) {
        app_fail('the database table ' . $m[1] . ' is missing. Import the newest schema files in phpMyAdmin.');
    }
    if (preg_match("/Unknown column '([^']+)'/i", $msg, $m)) {
        app_fail('the database column ' . $m[1] . ' is missing. Import the newest schema files in phpMyAdmin.');
    }
    app_fail('something went wrong on the server. Details are in the website’s error log.');
});
register_shutdown_function(function () {
    $err = error_get_last();
    if ($err && in_array($err['type'], array(E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR), true)) {
        error_log('grocery_app_api fatal: ' . $err['message'] . ' @ ' . $err['file'] . ':' . $err['line']);
        app_fail(mb_substr(preg_replace('/ in \/.*$/s', '', $err['message']), 0, 160));
    }
});

/* Create what the app needs if schema_v27 was never imported (or its import failed),
   the same way Family Manager sets up its own table. Safe to run every time. */
function app_setup($withSyncColumns)
{
    db()->exec('CREATE TABLE IF NOT EXISTS family_app_tokens (
        id            INT AUTO_INCREMENT PRIMARY KEY,
        user_id       INT          NOT NULL,
        token_hash    CHAR(64)     NOT NULL,
        device        VARCHAR(80)  NULL,
        created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        last_used_at  TIMESTAMP    NULL,
        UNIQUE KEY uq_token (token_hash),
        KEY idx_user (user_id)
    ) ENGINE=InnoDB');
    if (!$withSyncColumns) { return; }
    foreach (array('family_shopping_lists', 'family_shopping_items') as $t) {
        $st = db()->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
                              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'updated_at'");
        $st->execute(array($t));
        if ((int) $st->fetchColumn() === 0) {
            db()->exec("ALTER TABLE $t ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        }
    }
}
if ($action === 'login') { app_setup(false); }
if ($action === 'sync')  { app_setup(true); }
$in = json_decode((string) file_get_contents('php://input'), true);
if (!is_array($in)) { $in = array(); }

function app_str($a, $k, $def = '') { return isset($a[$k]) && (is_string($a[$k]) || is_numeric($a[$k])) ? trim((string) $a[$k]) : $def; }
function app_num($a, $k, $def = 0)  { return isset($a[$k]) && is_numeric($a[$k]) ? (float) $a[$k] : $def; }
function app_bool($a, $k)           { return !empty($a[$k]) ? 1 : 0; }
function app_money($v)              { return $v === null || $v === '' || !is_numeric($v) ? null : max(0, min(99999999, round((float) $v, 2))); }

/* The signed-in family member for this phone's key, or a 401. */
function app_user()
{
    $tok = '';
    $h = isset($_SERVER['HTTP_AUTHORIZATION']) ? $_SERVER['HTTP_AUTHORIZATION']
       : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) ? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] : '');
    if (preg_match('/^Bearer\s+([A-Za-z0-9]{20,128})$/', trim($h), $m)) { $tok = $m[1]; }
    if ($tok === '' && !empty($_SERVER['HTTP_X_APP_TOKEN']) && preg_match('/^[A-Za-z0-9]{20,128}$/', $_SERVER['HTTP_X_APP_TOKEN'])) {
        $tok = $_SERVER['HTTP_X_APP_TOKEN'];
    }
    if ($tok === '') { json_out(array('ok' => false, 'error' => 'Not signed in.'), 401); }
    $stmt = db()->prepare(
        'SELECT t.id AS token_id, u.id, u.username, u.display_name, u.role, u.family_id
           FROM family_app_tokens t JOIN family_users u ON u.id = t.user_id
          WHERE t.token_hash = ?'
    );
    $stmt->execute(array(hash('sha256', $tok)));
    $u = $stmt->fetch();
    if (!$u) { json_out(array('ok' => false, 'error' => 'This phone was signed out. Sign in again.'), 401); }
    db()->prepare('UPDATE family_app_tokens SET last_used_at = NOW() WHERE id = ?')->execute(array($u['token_id']));
    return $u;
}

function app_user_out($u)
{
    $fam = db()->prepare('SELECT family_name FROM family_families WHERE id = ?');
    $fam->execute(array((int) $u['family_id']));
    return array('username' => $u['username'], 'display_name' => $u['display_name'],
                 'role' => $u['role'], 'family_name' => (string) $fam->fetchColumn());
}

/* Does family_stores keep a family_id? (Added by family_scope.php; older sites don't have it.) */
function app_stores_scoped()
{
    static $scoped = null;
    if ($scoped === null) {
        try {
            $st = db()->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS
                                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'family_stores' AND COLUMN_NAME = 'family_id'");
            $st->execute();
            $scoped = ((int) $st->fetchColumn()) > 0;
        } catch (Exception $e) { $scoped = false; }
    }
    return $scoped;
}

/* Store id for a store name: this family's own store or a built-in one (adds it to the family if new), or null for none. */
function app_store_id($name, $fam)
{
    $name = trim((string) $name);
    if ($name === '') { return null; }
    if (app_stores_scoped()) {
        $stmt = db()->prepare('SELECT id FROM family_stores
                                WHERE LOWER(name) = LOWER(?) AND (family_id = ? OR (is_default = 1 AND family_id IS NULL))
                             ORDER BY family_id IS NULL, id LIMIT 1');
        $stmt->execute(array($name, (int) $fam));
    } else {
        $stmt = db()->prepare('SELECT id FROM family_stores WHERE LOWER(name) = LOWER(?) LIMIT 1');
        $stmt->execute(array($name));
    }
    $id = $stmt->fetchColumn();
    if ($id) { return (int) $id; }
    if (app_stores_scoped()) {
        db()->prepare('INSERT INTO family_stores (name, sort_order, family_id) VALUES (?, 50, ?)')->execute(array(mb_substr($name, 0, 80), (int) $fam));
    } else {
        db()->prepare('INSERT INTO family_stores (name, sort_order) VALUES (?, 50)')->execute(array(mb_substr($name, 0, 80)));
    }
    return (int) db()->lastInsertId();
}

/* Split the phone's "size" text ("2 lb") into the website's quantity + unit. */
function app_split_size($size)
{
    $size = trim((string) $size);
    if (preg_match('/^([0-9]+(?:[.,][0-9]+)?)\s*(.*)$/', $size, $m)) { return array(mb_substr($m[1], 0, 50), mb_substr(trim($m[2]), 0, 50)); }
    return array(mb_substr($size, 0, 50), '');
}

switch ($action) {

    case 'login': {
        $username = app_str($in, 'username');
        $password = isset($in['password']) && is_string($in['password']) ? $in['password'] : '';
        $res = attempt_login($username, $password);
        if (empty($res['ok'])) {
            json_out(array('ok' => false, 'error' => isset($res['error']) ? $res['error'] : 'Could not sign in.'), 401);
        }
        $user = $res['user'];
        if (empty($user['family_id'])) {
            json_out(array('ok' => false, 'error' => 'Your account isn’t part of a family yet. Join one on the website first.'), 403);
        }
        $token = bin2hex(openssl_random_pseudo_bytes(32));
        db()->prepare('INSERT INTO family_app_tokens (user_id, token_hash, device, last_used_at) VALUES (?,?,?,NOW())')
             ->execute(array((int) $user['id'], hash('sha256', $token), mb_substr(app_str($in, 'device', 'Phone'), 0, 80)));
        json_out(array('ok' => true, 'token' => $token, 'user' => app_user_out($user)));
    }

    case 'logout': {
        $u = app_user();
        db()->prepare('DELETE FROM family_app_tokens WHERE id = ?')->execute(array($u['token_id']));
        json_out(array('ok' => true));
    }

    case 'whoami': {
        $u = app_user();
        json_out(array('ok' => true, 'user' => app_user_out($u)));
    }

    case 'sync': {
        $u = app_user();
        $fam = (int) $u['family_id'];
        $parent = $u['role'] === 'parent';
        $base = app_str($in, 'base', '');
        if (!preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $base)) { $base = '1970-01-01 00:00:00'; }
        $map = array('lists' => array(), 'items' => array());
        $conflicts = 0;
        $db = db();
        $db->beginTransaction();
        try {
            /* ---- lists ---- */
            $lists = isset($in['lists']) && is_array($in['lists']) ? array_slice($in['lists'], 0, 200) : array();
            $own = $db->prepare('SELECT id, updated_at FROM family_shopping_lists WHERE id = ? AND family_id = ?');
            foreach ($lists as $L) {
                if (!is_array($L)) { continue; }
                $cid = app_str($L, 'cid');
                $rid = (int) app_num($L, 'rid', 0);
                if ($rid) {
                    $own->execute(array($rid, $fam));
                    $row = $own->fetch();
                    if (!$row) { continue; }                     // gone or not ours: the snapshot will tell the phone
                    if ($row['updated_at'] > $base) { $conflicts++; $map['lists'][$cid] = $rid; continue; }
                    if (!empty($L['deleted'])) {
                        $db->prepare('DELETE FROM family_shopping_lists WHERE id = ?')->execute(array($rid));
                        continue;
                    }
                    $db->prepare('UPDATE family_shopping_lists SET name=?, store_id=?, tax_rate=?, sort_order=? WHERE id=?')
                       ->execute(array(mb_substr(app_str($L, 'name', 'List'), 0, 80), app_store_id(app_str($L, 'store'), $fam),
                                       min(100, max(0, round(app_num($L, 'tax_rate'), 3))), (int) app_num($L, 'sort'), $rid));
                    $map['lists'][$cid] = $rid;
                } elseif (empty($L['deleted'])) {
                    $name = app_str($L, 'name');
                    if ($name === '') { $name = 'List'; }
                    $db->prepare('INSERT INTO family_shopping_lists (family_id, name, store_id, tax_rate, sort_order, created_by) VALUES (?,?,?,?,?,?)')
                       ->execute(array($fam, mb_substr($name, 0, 80), app_store_id(app_str($L, 'store'), $fam),
                                       min(100, max(0, round(app_num($L, 'tax_rate'), 3))), (int) app_num($L, 'sort'), (int) $u['id']));
                    $map['lists'][$cid] = (int) $db->lastInsertId();
                }
            }

            /* ---- items ---- */
            $items = isset($in['items']) && is_array($in['items']) ? array_slice($in['items'], 0, 3000) : array();
            $ownList = $db->prepare('SELECT id, store_id FROM family_shopping_lists WHERE id = ? AND family_id = ?');
            $ownItem = $db->prepare(
                'SELECT i.id, i.updated_at FROM family_shopping_items i
                   JOIN family_shopping_lists l ON l.id = i.list_id WHERE i.id = ? AND l.family_id = ?'
            );
            foreach ($items as $I) {
                if (!is_array($I)) { continue; }
                $cid = app_str($I, 'cid');
                $rid = (int) app_num($I, 'rid', 0);
                // which list: the phone's list key from this sync, or a known website id
                $listId = (int) app_num($I, 'list_rid', 0);
                $lcid = app_str($I, 'list_cid');
                if ($lcid !== '' && isset($map['lists'][$lcid])) { $listId = (int) $map['lists'][$lcid]; }
                $ownList->execute(array($listId, $fam));
                $list = $ownList->fetch();

                list($qty, $unit) = app_split_size(app_str($I, 'size'));
                $wic = app_str($I, 'wic_cat');
                $cpType = app_str($I, 'coupon_type');
                $fields = array(
                    'name'         => mb_substr(app_str($I, 'name', 'Item'), 0, 200),
                    'quantity'     => $qty,
                    'unit'         => $unit,
                    'category'     => mb_substr(app_str($I, 'category', 'General') ?: 'General', 0, 80),
                    'price'        => app_money(isset($I['price']) ? $I['price'] : null),
                    'mult'         => max(1, min(999, (int) app_num($I, 'qty', 1))),
                    'taxable'      => app_bool($I, 'taxable'),
                    'checked'      => app_bool($I, 'checked'),
                    'sort_order'   => (int) app_num($I, 'sort'),
                    'snap'         => isset($I['snap']) ? app_bool($I, 'snap') : 1,
                    'wic_cat'      => preg_match('/^[a-z0-9_]{1,40}$/i', $wic) ? $wic : null,
                    'wic_units'    => max(0.01, min(9999, round(app_num($I, 'wic_units', 1), 2))),
                    'coupon_type'  => in_array($cpType, array('amt', 'pct', 'bogo'), true) ? $cpType : null,
                    'coupon_value' => app_money(isset($I['coupon_value']) ? $I['coupon_value'] : null),
                    'coupon_each'  => app_bool($I, 'coupon_each'),
                    'coupon_mfr'   => app_bool($I, 'coupon_mfr'),
                );

                if ($rid) {
                    $ownItem->execute(array($rid, $fam));
                    $row = $ownItem->fetch();
                    if (!$row) { continue; }
                    if ($row['updated_at'] > $base) { $conflicts++; $map['items'][$cid] = $rid; continue; }
                    if (!empty($I['deleted'])) {
                        $db->prepare('DELETE FROM family_shopping_items WHERE id = ?')->execute(array($rid));
                        continue;
                    }
                    $sets = array(); $vals = array();
                    foreach ($fields as $k => $v) { $sets[] = $k . ' = ?'; $vals[] = $v; }
                    if ($list) { $sets[] = 'list_id = ?'; $vals[] = (int) $list['id']; $sets[] = 'store_id = ?'; $vals[] = $list['store_id']; }
                    $vals[] = $rid;
                    $db->prepare('UPDATE family_shopping_items SET ' . implode(', ', $sets) . ' WHERE id = ?')->execute($vals);
                    $map['items'][$cid] = $rid;
                } elseif (empty($I['deleted']) && $list) {
                    $cols = array_keys($fields);
                    $vals = array_values($fields);
                    $cols[] = 'list_id';  $vals[] = (int) $list['id'];
                    $cols[] = 'store_id'; $vals[] = $list['store_id'];
                    $cols[] = 'added_by'; $vals[] = (int) $u['id'];
                    $db->prepare('INSERT INTO family_shopping_items (' . implode(',', $cols) . ') VALUES (' .
                                 implode(',', array_fill(0, count($cols), '?')) . ')')->execute($vals);
                    $map['items'][$cid] = (int) $db->lastInsertId();
                }
            }

            /* ---- WIC / SNAP (parents only, like the website) ---- */
            if ($parent && isset($in['benefits']) && is_array($in['benefits'])) {
                $B = $in['benefits'];
                $cur = $db->prepare('SELECT updated_at FROM family_shop_benefits WHERE family_id = ?');
                $cur->execute(array($fam));
                $at = $cur->fetchColumn();
                if ($at && $at > $base) {
                    $conflicts++;
                } else {
                    $foods = array();
                    if (isset($B['wic_foods']) && is_array($B['wic_foods'])) {
                        foreach ($B['wic_foods'] as $k => $v) {
                            if (count($foods) >= 40 || !preg_match('/^[a-z0-9_]{1,40}$/i', (string) $k)) { continue; }
                            $foods[$k] = (is_numeric($v) && $v !== '') ? (string) max(0, min(99999, round((float) $v, 2))) : '';
                        }
                    }
                    $custom = array();
                    if (isset($B['wic_custom']) && is_array($B['wic_custom'])) {
                        foreach ($B['wic_custom'] as $k => $v) {
                            if (count($custom) >= 20 || !preg_match('/^c_[a-z0-9_]{1,37}$/i', (string) $k) || !is_array($v)) { continue; }
                            $label = isset($v['label']) ? trim((string) $v['label']) : '';
                            if ($label === '') { continue; }
                            $custom[$k] = array('label' => mb_substr($label, 0, 40),
                                                'unit' => mb_substr(isset($v['unit']) && trim($v['unit']) !== '' ? trim((string) $v['unit']) : 'each', 0, 12));
                        }
                    }
                    $db->prepare(
                        'INSERT INTO family_shop_benefits (family_id, wic_on, snap_on, snap_balance, cvb_balance, wic_foods, wic_custom, tax_mfr, updated_by)
                         VALUES (?,?,?,?,?,?,?,?,?)
                         ON DUPLICATE KEY UPDATE wic_on=VALUES(wic_on), snap_on=VALUES(snap_on), snap_balance=VALUES(snap_balance),
                           cvb_balance=VALUES(cvb_balance), wic_foods=VALUES(wic_foods), wic_custom=VALUES(wic_custom),
                           tax_mfr=VALUES(tax_mfr), updated_by=VALUES(updated_by)'
                    )->execute(array($fam, app_bool($B, 'wic_on'), app_bool($B, 'snap_on'),
                                     (float) app_money(isset($B['snap_balance']) ? $B['snap_balance'] : 0),
                                     (float) app_money(isset($B['cvb_balance']) ? $B['cvb_balance'] : 0),
                                     json_encode((object) $foods), json_encode((object) $custom),
                                     isset($B['tax_mfr']) ? app_bool($B, 'tax_mfr') : 1, (int) $u['id']));
                }
            }
            $db->commit();
        } catch (Exception $e) {
            $db->rollBack();
            error_log('grocery_app_api sync: ' . $e->getMessage());
            json_out(array('ok' => false, 'error' => 'The website couldn’t save the changes. Try again.'), 500);
        }

        /* ---- snapshot of everything the family has now ---- */
        $now = $db->query('SELECT NOW()')->fetchColumn();
        $ls = $db->prepare(
            'SELECT l.id, l.name, l.tax_rate, l.sort_order, s.name AS store
               FROM family_shopping_lists l LEFT JOIN family_stores s ON s.id = l.store_id
              WHERE l.family_id = ? ORDER BY l.sort_order, l.id'
        );
        $ls->execute(array($fam));
        $out = array(); $byId = array();
        foreach ($ls->fetchAll() as $r) {
            $byId[(int) $r['id']] = count($out);
            $out[] = array('rid' => (int) $r['id'], 'name' => $r['name'], 'store' => (string) $r['store'],
                           'tax_rate' => (float) $r['tax_rate'], 'sort' => (int) $r['sort_order'], 'items' => array());
        }
        if ($byId) {
            $in2 = implode(',', array_fill(0, count($byId), '?'));
            $is = $db->prepare(
                'SELECT id, list_id, name, quantity, unit, category, price, mult, taxable, checked, sort_order,
                        snap, wic_cat, wic_units, coupon_type, coupon_value, coupon_each, coupon_mfr
                   FROM family_shopping_items WHERE list_id IN (' . $in2 . ') ORDER BY sort_order, id'
            );
            $is->execute(array_keys($byId));
            foreach ($is->fetchAll() as $r) {
                $out[$byId[(int) $r['list_id']]]['items'][] = array(
                    'rid' => (int) $r['id'], 'name' => $r['name'],
                    'size' => trim(trim((string) $r['quantity']) . ' ' . trim((string) $r['unit'])),
                    'category' => (string) $r['category'],
                    'price' => $r['price'] === null ? null : (float) $r['price'],
                    'qty' => max(1, (int) $r['mult']), 'taxable' => (int) $r['taxable'], 'checked' => (int) $r['checked'],
                    'sort' => (int) $r['sort_order'], 'snap' => (int) $r['snap'],
                    'wic_cat' => $r['wic_cat'], 'wic_units' => (float) $r['wic_units'],
                    'coupon_type' => $r['coupon_type'],
                    'coupon_value' => $r['coupon_value'] === null ? null : (float) $r['coupon_value'],
                    'coupon_each' => (int) $r['coupon_each'], 'coupon_mfr' => (int) $r['coupon_mfr'],
                );
            }
        }
        $bs = $db->prepare('SELECT * FROM family_shop_benefits WHERE family_id = ?');
        $bs->execute(array($fam));
        $b = $bs->fetch();
        $foods = $b && $b['wic_foods'] ? json_decode($b['wic_foods'], true) : array();
        $custom = $b && $b['wic_custom'] ? json_decode($b['wic_custom'], true) : array();
        $benefits = array(
            'wic_on' => $b ? (int) $b['wic_on'] : 0, 'snap_on' => $b ? (int) $b['snap_on'] : 0,
            'snap_balance' => $b ? (float) $b['snap_balance'] : 0, 'cvb_balance' => $b ? (float) $b['cvb_balance'] : 0,
            'wic_foods' => (object) (is_array($foods) ? $foods : array()), 'wic_custom' => (object) (is_array($custom) ? $custom : array()),
            'tax_mfr' => $b ? (int) $b['tax_mfr'] : 1, 'exists' => $b ? 1 : 0,
        );
        json_out(array(
            'ok' => true, 'server_time' => $now, 'conflicts' => $conflicts,
            'map' => array('lists' => (object) $map['lists'], 'items' => (object) $map['items']),
            'snapshot' => array('lists' => $out, 'benefits' => $benefits, 'can_edit_benefits' => $parent ? 1 : 0),
            'user' => app_user_out($u),
        ));
    }

    default:
        json_out(array('ok' => false, 'error' => 'Unknown action.'), 400);
}
