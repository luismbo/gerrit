/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-key-binding-display',
    _legacyUndefinedCheck: true,

    properties: {
      /** @type {Array<string>} */
      binding: Array,
    },

    _computeModifiers(binding) {
      return binding.slice(0, binding.length - 1);
    },

    _computeKey(binding) {
      return binding[binding.length - 1];
    },
  });
})();
