# WorldGen Editor: guide по `continents.json`

Мод теперь работает в стиле datapack/world preset. Это значит, что для нового мира нужно выбрать тип мира:

```text
WorldGen Editor: Islands
```

Внутри этого пресета Minecraft использует обычный `minecraft:noise` генератор, но с двумя модовыми ресурсами:

- `worldgen_editor:island_biome_source` - заранее выбирает океан/сушу на уровне biome source.
- `worldgen_editor:island_final_density` - мягко меняет vanilla density, чтобы океан был вокруг островов.

Это намного стабильнее, чем старая runtime-подмена уже созданного генератора.

## Где лежит конфиг

Основной файл:

```text
config/worldgen_editor/continents.json
```

Если файла нет, мод создаст базовый пример сам.

После изменения конфига в уже запущенном мире выполни:

```text
/worldgen_editor reload
```

`reload` влияет только на новые чанки. Уже сгенерированные чанки, биомы, структуры и блоки Minecraft не пересоздает.

## Включение

В обычном конфиге есть верхнеуровневый параметр:

```json
{
  "enabled": true,
  "entries": []
}
```

- `enabled: false` - островная маска не применяется, даже если выбран island preset.
- `enabled: true` - островная генерация разрешена для миров, где world-флаг тоже включен.

Для каждого мира мод хранит отдельный файл:

```text
<папка_мира>/worldgen_editor/worldgen_editor.json
```

Новый файл создается с:

```json
{
  "enabled": true
}
```

Итоговая логика:

```text
генерация включена = continents.json enabled && worldgen_editor.json enabled
```

Команды:

```text
/worldgen_editor enable
/worldgen_editor disable
/worldgen_editor status
/worldgen_editor reload
```

## Самый простой остров

Минимально нужны центр и радиус:

```json
{
  "enabled": true,
  "entries": [
    {
      "x": 0,
      "z": 0,
      "radius": 850
    }
  ]
}
```

Такой остров появится вокруг координат `0, 0`.

## Рекомендуемый пример

```json
{
  "enabled": true,
  "entries": [
    {
      "name": "Spawn Island",
      "x": 0,
      "z": 0,
      "radius": 950,
      "roughness": 0.16,
      "shore_width": 0.18,
      "noise": {
        "seed": "spawn"
      }
    },
    {
      "name": "Long Island",
      "x": 1350,
      "z": -650,
      "radius": 520,
      "stretch_x": 1.7,
      "stretch_z": 0.75,
      "rotation": 25,
      "roughness": 0.14,
      "noise": {
        "seed": "long_island"
      }
    }
  ]
}
```

## Основные поля острова

- `name` - необязательное имя для удобства.
- `x` - центр острова по X.
- `z` - центр острова по Z.
- `radius` - базовый размер острова.
- `radius_x` и `radius_z` - отдельные радиусы, если нужна точная вытянутая форма.
- `stretch_x` и `stretch_z` - множители растяжения при использовании `radius`.
- `rotation` - поворот острова в градусах.
- `roughness` - сила шума берега от `0.0` до `1.0`.
- `shore_width` - ширина мягкого перехода между сушей и океаном.
- `overlap` - можно ли маске острова усиливать другие острова.

Обычно достаточно менять `x`, `z`, `radius`, `stretch_x`, `stretch_z`, `rotation` и `roughness`.

## Шум берега

```json
{
  "x": 0,
  "z": 0,
  "radius": 700,
  "roughness": 0.22,
  "noise": {
    "seed": "coast_1",
    "scale": 3.5,
    "first_octave": -1,
    "amplitudes": [1.0, 0.78, 0.55, 0.34]
  }
}
```

- `noise.seed` меняет форму берега, но не центр и размер острова.
- `noise.scale` повышает частоту шума.
- `noise.first_octave` обычно можно оставить `-1`.
- `noise.amplitudes` управляет вкладом октав.

Более гладкий берег:

```json
"roughness": 0.08
```

Более рваный берег:

```json
"roughness": 0.25
```

## Совместимость со старым форматом

Мод продолжает понимать старые имена:

- `center_x` = `x`
- `center_z` = `z`
- `x_divisor` = `radius_x`
- `z_divisor` = `radius_z`
- `rotation_degrees` = `rotation`
- `multiplier` = `size_multiplier`

Новый формат короче, но старые datapack-style файлы не обязаны ломаться.

## Важные ограничения

- Для островной генерации выбирай пресет `WorldGen Editor: Islands` при создании мира.
- Переключение конфига после создания мира не пересоздает старые чанки.
- Если хочешь полностью новый результат без конфликтов, создай новый мир или удали старые region-файлы осознанно.
- Острова используют vanilla land biomes внутри маски. Мод не рисует реки и болота вручную.
- Океан вокруг островов создается через noise settings и biome source, а не поздней подменой блоков.

## Частые проблемы

- Остров не появился: проверь, что выбран world preset `WorldGen Editor: Islands`.
- Мир выглядит vanilla: проверь `enabled` в `continents.json` и `/worldgen_editor status`.
- Изменил JSON, но рядом ничего не поменялось: ты смотришь на уже сгенерированные чанки.
- Сломался reload: проверь запятые, кавычки и обязательные поля `x`, `z`, `radius`.
- Слишком острые берега: уменьши `roughness` или увеличь `shore_width`.
- Остров слишком низкий или маленький: увеличь `radius`; очень маленькие острова чаще попадают под vanilla lowland/river terrain.
