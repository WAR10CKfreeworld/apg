/*
 * Copyright (C) 2014 Ash Hughes <ashes-iontach@hotmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.thialfihar.android.apg.service;

import android.os.Parcel;
import android.os.Parcelable;

import org.thialfihar.android.apg.pgp.Key;

import java.util.ArrayList;
import java.util.GregorianCalendar;

public class SaveKeyringParcel implements Parcelable {

    public ArrayList<String> userIDs;
    public ArrayList<String> originalIDs;
    public ArrayList<String> deletedIDs;
    public boolean[] newIDs;
    public boolean primaryIDChanged;
    public boolean[] moddedKeys;
    public ArrayList<Key> deletedKeys;
    public ArrayList<GregorianCalendar> keysExpiryDates;
    public ArrayList<Integer> keysUsages;
    public String newPassphrase;
    public String oldPassphrase;
    public boolean[] newKeys;
    public ArrayList<Key> keys;
    public String originalPrimaryID;

    public SaveKeyringParcel() {}

    private SaveKeyringParcel(Parcel source) {
        userIDs = (ArrayList<String>) source.readSerializable();
        originalIDs = (ArrayList<String>) source.readSerializable();
        deletedIDs = (ArrayList<String>) source.readSerializable();
        newIDs = source.createBooleanArray();
        primaryIDChanged = source.readByte() != 0;
        moddedKeys = source.createBooleanArray();
        deletedKeys = (ArrayList<Key>) source.readSerializable();
        keysExpiryDates = (ArrayList<GregorianCalendar>) source.readSerializable();
        keysUsages = source.readArrayList(Integer.class.getClassLoader());
        newPassphrase = source.readString();
        oldPassphrase = source.readString();
        newKeys = source.createBooleanArray();
        keys = (ArrayList<Key>) source.readSerializable();
        originalPrimaryID = source.readString();
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeSerializable(userIDs); //might not be the best method to store.
        destination.writeSerializable(originalIDs);
        destination.writeSerializable(deletedIDs);
        destination.writeBooleanArray(newIDs);
        destination.writeByte((byte) (primaryIDChanged ? 1 : 0));
        destination.writeBooleanArray(moddedKeys);
        destination.writeSerializable(deletedKeys);
        destination.writeSerializable(keysExpiryDates);
        destination.writeList(keysUsages);
        destination.writeString(newPassphrase);
        destination.writeString(oldPassphrase);
        destination.writeBooleanArray(newKeys);
        destination.writeSerializable(keys);
        destination.writeString(originalPrimaryID);
    }

    public static final Creator<SaveKeyringParcel> CREATOR = new Creator<SaveKeyringParcel>() {
        public SaveKeyringParcel createFromParcel(final Parcel source) {
            return new SaveKeyringParcel(source);
        }

        public SaveKeyringParcel[] newArray(final int size) {
            return new SaveKeyringParcel[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
