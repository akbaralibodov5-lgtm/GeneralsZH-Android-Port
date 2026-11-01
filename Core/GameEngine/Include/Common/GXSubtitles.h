/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILTY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// Waru Edition integration of the v1.4.2 subtitle toggle.
//
// The launcher creates/deletes a file named "subtitles" in the app's private
// files directory. The engine reads it once per process. Voice-line subtitle
// strings are stored in the game's string table with a leading '*' marker;
// when enabled, the UI may display the text without those marker characters.
// This setting is presentation-only and must not affect simulation state.

#pragma once

#if defined(__ANDROID__)
#include <cstdio>
#include <unistd.h>
#endif

namespace GXSubtitles
{
	inline bool enabled()
	{
		static int s_state = -1;
		if (s_state < 0)
		{
			s_state = 0;
#if defined(__ANDROID__)
			char path[256];
			const int userId = (int)(getuid() / 100000);
			snprintf(path, sizeof(path), "/data/user/%d/com.generalsx.zerohour/files/subtitles", userId);
			s_state = access(path, F_OK) == 0 ? 1 : 0;
#endif
		}
		return s_state == 1;
	}

	inline bool hasWords(const wchar_t *text)
	{
		if (!enabled() || text == nullptr)
			return false;
		for (; *text != 0; ++text)
		{
			if (*text != L'*' && *text != L' ')
				return true;
		}
		return false;
	}
}
