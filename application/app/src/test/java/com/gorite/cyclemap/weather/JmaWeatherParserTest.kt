package com.gorite.cyclemap.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JmaWeatherParserTest {

    private val sampleResolvedArea = ResolvedArea(
        officeCode = "350000",
        class10Code = "350020",
        amedasCode = "51106",
        areaName = "山口県 (中部)",
        prefectureName = "山口県"
    )

    private val sampleJson = """
    [
      {
        "publishingOffice": "下関地方気象台",
        "reportDatetime": "2026-10-16T17:00:00+09:00",
        "timeSeries": [
          {
            "timeDefines": [
              "2026-10-16T17:00:00+09:00",
              "2026-10-17T00:00:00+09:00"
            ],
            "areas": [
              {
                "area": { "name": "中部", "code": "350020" },
                "weatherCodes": ["200", "100"],
                "weathers": ["くもり　夜　晴れ", "晴れ　時々　くもり"],
                "winds": ["北の風", "北西の風"],
                "waves": ["０．５メートル", "０．５メートル"]
              }
            ]
          },
          {
            "timeDefines": [
              "2026-10-16T18:00:00+09:00",
              "2026-10-17T00:00:00+09:00",
              "2026-10-17T06:00:00+09:00"
            ],
            "areas": [
              {
                "area": { "name": "中部", "code": "350020" },
                "pops": ["10", "0", "0"]
              }
            ]
          },
          {
            "timeDefines": [
              "2026-10-17T00:00:00+09:00",
              "2026-10-17T09:00:00+09:00"
            ],
            "areas": [
              {
                "area": { "name": "山口", "code": "51106" },
                "temps": ["14", "23"]
              }
            ]
          }
        ]
      }
    ]
    """.trimIndent()

    @Test
    fun parse_extractsAllFieldsCorrectly() {
        val result = JmaWeatherParser.parse(sampleJson, sampleResolvedArea)
        assertNotNull(result)
        assertEquals("350000", result?.officeCode)
        assertEquals("350020", result?.class10Code)
        assertEquals("51106", result?.amedasCode)
        assertEquals("くもり 夜 晴れ", result?.weatherText)
        assertEquals("200", result?.weatherCode)
        assertEquals(10, result?.currentPop)
        assertEquals(14.0, result?.currentTemp)
        assertEquals(3, result?.popList?.size)
        assertEquals(2, result?.tempList?.size)
    }

    @Test
    fun parse_missingTemperature_returnsNullNotZero() {
        val jsonWithoutTemps = """
        [
          {
            "publishingOffice": "下関地方気象台",
            "reportDatetime": "2026-10-16T17:00:00+09:00",
            "timeSeries": [
              {
                "timeDefines": ["2026-10-16T17:00:00+09:00"],
                "areas": [
                  {
                    "area": { "name": "中部", "code": "350020" },
                    "weatherCodes": ["100"],
                    "weathers": ["晴れ"]
                  }
                ]
              },
              {
                "timeDefines": ["2026-10-16T18:00:00+09:00"],
                "areas": [
                  {
                    "area": { "name": "中部", "code": "350020" },
                    "pops": ["0"]
                  }
                ]
              },
              {
                "timeDefines": ["2026-10-17T00:00:00+09:00"],
                "areas": [
                  {
                    "area": { "name": "山口", "code": "51106" },
                    "temps": [""]
                  }
                ]
              }
            ]
          }
        ]
        """.trimIndent()

        val result = JmaWeatherParser.parse(jsonWithoutTemps, sampleResolvedArea)
        assertNotNull(result)
        assertEquals("100", result?.weatherCode)
        assertEquals("晴れ", result?.weatherText)
        // 気温が空文字の場合は 0.0 ではなく null であること（仕様書厳守）
        assertNull("空文字の気温は null になること", result?.currentTemp)
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        val result = JmaWeatherParser.parse("invalid json string", sampleResolvedArea)
        assertNull(result)
    }

    @Test
    fun parse_emptyFirstTemperatureSlot_picksSecondValidSlot() {
        val jsonAfternoon = sampleJson.replace("\"temps\": [\"14\", \"23\"]", "\"temps\": [\"\", \"23\"]")
        val result = JmaWeatherParser.parse(jsonAfternoon, sampleResolvedArea)
        assertNotNull(result)
        assertEquals(23.0, result?.currentTemp)
    }
}
