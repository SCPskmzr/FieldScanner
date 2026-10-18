# Каталог контуров результата (`visual_id`)

Закрытый плоский список из 100 архетипов. Источник истины — `ScanVisual` в
`app/src/main/java/com/fieldscanner/app/data/finding/ScanOutlineCatalog.kt`.
Модель анализа обязана вернуть ровно одно значение `visual_id` из этого списка.
Порядок нормативен и совпадает с порядком enum.

Все файлы кладутся в `app/src/main/assets/scan_outlines/` и рисуются по контракту
в `README.md` (артборд `viewBox="0 0 640 200"`, один открытый путь `id="outline"`).
Имя файла = идентификатор в нижнем регистре + `.svg` (`CAT` → `cat.svg`,
`PEN_PENCIL` → `pen_pencil.svg`).

## Архетипы

| # | `visual_id` | Файл | Значение |
| --- | --- | --- | --- |
| 1 | `PERSON` | `person.svg` | человек |
| 2 | `FACE` | `face.svg` | лицо |
| 3 | `HAND` | `hand.svg` | рука |
| 4 | `DOG` | `dog.svg` | собака |
| 5 | `CAT` | `cat.svg` | кошка |
| 6 | `BIRD` | `bird.svg` | птица |
| 7 | `FISH` | `fish.svg` | рыба |
| 8 | `FARM_ANIMAL` | `farm_animal.svg` | сельскохозяйственное животное |
| 9 | `SMALL_MAMMAL` | `small_mammal.svg` | мелкое млекопитающее |
| 10 | `INSECT` | `insect.svg` | насекомое |
| 11 | `BUTTERFLY` | `butterfly.svg` | бабочка |
| 12 | `SPIDER` | `spider.svg` | паук |
| 13 | `SNAIL` | `snail.svg` | улитка |
| 14 | `REPTILE_AMPHIBIAN` | `reptile_amphibian.svg` | рептилия / земноводное |
| 15 | `BALL` | `ball.svg` | мяч |
| 16 | `TOY_CAR` | `toy_car.svg` | игрушечная машинка |
| 17 | `TOY_FIGURE` | `toy_figure.svg` | игрушечная фигурка |
| 18 | `PLUSH_TOY` | `plush_toy.svg` | мягкая игрушка |
| 19 | `BUILDING_BLOCKS` | `building_blocks.svg` | конструктор / кубики |
| 20 | `TOY_WEAPON` | `toy_weapon.svg` | игрушечное оружие |
| 21 | `BOARD_GAME` | `board_game.svg` | настольная игра |
| 22 | `CARD` | `card.svg` | карточка / игровая карта |
| 23 | `PUZZLE` | `puzzle.svg` | пазл |
| 24 | `PHONE` | `phone.svg` | телефон |
| 25 | `LAPTOP` | `laptop.svg` | ноутбук |
| 26 | `HEADPHONES` | `headphones.svg` | наушники |
| 27 | `ELECTRONIC_DEVICE` | `electronic_device.svg` | электронное устройство |
| 28 | `CAR` | `car.svg` | автомобиль |
| 29 | `TRUCK` | `truck.svg` | грузовик |
| 30 | `BUS` | `bus.svg` | автобус |
| 31 | `BICYCLE` | `bicycle.svg` | велосипед |
| 32 | `MOTORCYCLE` | `motorcycle.svg` | мотоцикл |
| 33 | `TRAIN` | `train.svg` | поезд |
| 34 | `CONSTRUCTION_VEHICLE` | `construction_vehicle.svg` | строительная техника |
| 35 | `AIRCRAFT` | `aircraft.svg` | летательный аппарат |
| 36 | `BOAT` | `boat.svg` | лодка / корабль |
| 37 | `LAMP` | `lamp.svg` | лампа / светильник |
| 38 | `DOOR` | `door.svg` | дверь |
| 39 | `WINDOW` | `window.svg` | окно |
| 40 | `MIRROR` | `mirror.svg` | зеркало |
| 41 | `FURNITURE` | `furniture.svg` | мебель |
| 42 | `BUILDING` | `building.svg` | здание |
| 43 | `CUP` | `cup.svg` | чашка / стакан |
| 44 | `BOTTLE` | `bottle.svg` | бутылка |
| 45 | `PLATE_BOWL` | `plate_bowl.svg` | тарелка / миска |
| 46 | `BOX` | `box.svg` | коробка |
| 47 | `BAG` | `bag.svg` | сумка / пакет |
| 48 | `BACKPACK` | `backpack.svg` | рюкзак |
| 49 | `KEY` | `key.svg` | ключ |
| 50 | `CLOCK_WATCH` | `clock_watch.svg` | часы |
| 51 | `GLASSES` | `glasses.svg` | очки |
| 52 | `UMBRELLA` | `umbrella.svg` | зонт |
| 53 | `COIN` | `coin.svg` | монета |
| 54 | `SHOE` | `shoe.svg` | обувь |
| 55 | `HAT` | `hat.svg` | головной убор |
| 56 | `SHIRT` | `shirt.svg` | футболка / рубашка |
| 57 | `PANTS` | `pants.svg` | брюки / штаны |
| 58 | `JACKET` | `jacket.svg` | куртка |
| 59 | `BOOK` | `book.svg` | книга |
| 60 | `PAPER` | `paper.svg` | бумага / лист бумаги |
| 61 | `PEN_PENCIL` | `pen_pencil.svg` | ручка / карандаш |
| 62 | `ART_CRAFTS` | `art_crafts.svg` | принадлежности для творчества / поделок |
| 63 | `HAND_TOOL` | `hand_tool.svg` | ручной инструмент |
| 64 | `METAL_PART` | `metal_part.svg` | металлическая деталь |
| 65 | `FRUIT` | `fruit.svg` | фрукт |
| 66 | `BREAD` | `bread.svg` | хлеб / выпечка |
| 67 | `SWEET` | `sweet.svg` | сладость |
| 68 | `DRINK` | `drink.svg` | напиток |
| 69 | `PREPARED_FOOD` | `prepared_food.svg` | готовая еда / блюдо |
| 70 | `CUTLERY` | `cutlery.svg` | столовые приборы |
| 71 | `POT_PAN` | `pot_pan.svg` | кастрюля / сковорода |
| 72 | `REFRIGERATOR` | `refrigerator.svg` | холодильник |
| 73 | `VACUUM_CLEANER` | `vacuum_cleaner.svg` | пылесос |
| 74 | `TOOTHBRUSH` | `toothbrush.svg` | зубная щётка |
| 75 | `JEWELRY` | `jewelry.svg` | украшение |
| 76 | `SPORTS_EQUIPMENT` | `sports_equipment.svg` | спортивный инвентарь |
| 77 | `MUSICAL_INSTRUMENT` | `musical_instrument.svg` | музыкальный инструмент |
| 78 | `PLAYGROUND_EQUIPMENT` | `playground_equipment.svg` | оборудование детской площадки |
| 79 | `HELMET` | `helmet.svg` | шлем |
| 80 | `BALLOON` | `balloon.svg` | воздушный шар |
| 81 | `STREET_SIGN` | `street_sign.svg` | дорожный знак / уличный указатель |
| 82 | `TRASH_LITTER` | `trash_litter.svg` | мусор |
| 83 | `BATTERY` | `battery.svg` | батарейка / аккумулятор |
| 84 | `CABLE_CHARGER` | `cable_charger.svg` | кабель / зарядное устройство |
| 85 | `TOILET` | `toilet.svg` | унитаз |
| 86 | `ROCK` | `rock.svg` | камень |
| 87 | `LEAF` | `leaf.svg` | лист |
| 88 | `FLOWER` | `flower.svg` | цветок |
| 89 | `STICK_BRANCH` | `stick_branch.svg` | палка / ветка |
| 90 | `TREE_PLANT` | `tree_plant.svg` | дерево / растение |
| 91 | `MUSHROOM` | `mushroom.svg` | гриб |
| 92 | `FIRE` | `fire.svg` | огонь |
| 93 | `WATER` | `water.svg` | вода |
| 94 | `SNOW_ICE` | `snow_ice.svg` | снег / лёд |
| 95 | `SAND_SOIL` | `sand_soil.svg` | песок / земля / грунт |
| 96 | `GLASS` | `glass.svg` | стекло |
| 97 | `BARK_WOOD` | `bark_wood.svg` | кора / древесина |
| 98 | `SHELL` | `shell.svg` | ракушка |
| 99 | `BONE` | `bone.svg` | кость |
| 100 | `UNKNOWN_OBJECT` | `unknown_object.svg` | неизвестный / неопределённый объект (терминальный фолбэк) |

## Порядок фолбэка

Для любого конкретного архетипа приложение пробует контуры по порядку:
запрошенный файл → `UNKNOWN_OBJECT`. `UNKNOWN_OBJECT` — единственный запасной
вариант и терминальная точка цепочки; обобщённых контуров категорий больше нет.
Если ни один файл не поставлен, строка результата остаётся в процедурном
(декоративном) состоянии без сбоя.

## Статус поставки

Поставлен только `cat.svg`. Остальные строки каталога, включая
`unknown_object.svg`, зарезервированы; до поставки они автоматически используют
процедурный фолбэк.
